const API_ORDERS = '/api/orders';
const API_INVENTORY = '/api/inventory';

// tabs
document.getElementById('tab-orders').addEventListener('click', () => showTab('orders'));
document.getElementById('tab-inventory').addEventListener('click', () => showTab('inventory'));

function showTab(name) {
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  document.querySelectorAll('.pane').forEach(p => p.classList.add('hidden'));
  document.getElementById('tab-' + name).classList.add('active');
  document.getElementById(name + '-section').classList.remove('hidden');
}

// items UI for order create
document.getElementById('add-item').addEventListener('click', () => {
  const container = document.getElementById('items-container');
  const row = document.createElement('div');
  row.className = 'item-row';
  row.innerHTML = `
    <input class="productId" placeholder="product-id">
    <input class="quantity" type="number" placeholder="qty" value="1" min="1">
    <button class="remove-item">Remove</button>
  `;
  row.querySelector('.remove-item').addEventListener('click', () => row.remove());
  container.appendChild(row);
});

// create order
document.getElementById('create-order').addEventListener('click', async () => {
  const customerId = document.getElementById('customerId').value;
  const items = Array.from(document.querySelectorAll('#items-container .item-row')).map(r => ({
    productId: r.querySelector('.productId').value,
    quantity: parseInt(r.querySelector('.quantity').value || '0', 10)
  })).filter(i => i.productId && i.quantity > 0);

  const payload = { customerId, items };
  try {
    const res = await fetch(API_ORDERS, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    const json = await res.json();
    document.getElementById('create-order-response').textContent = JSON.stringify(json, null, 2);
    // set newly created order id into get/cancel fields for convenience
    if (json.orderId) {
      document.getElementById('get-order-id').value = json.orderId;
      document.getElementById('cancel-order-id').value = json.orderId;
    }
    // start polling order status until it leaves CREATED
    pollOrderStatus(json.orderId, statusUpdate => {
      document.getElementById('create-order-response').textContent = JSON.stringify(statusUpdate, null, 2);
      // refresh lists when status becomes final
      if (statusUpdate.status && statusUpdate.status !== 'CREATED') {
        fetchOrdersList();
        fetchInventoryList();
      }
    });
  } catch (e) {
    document.getElementById('create-order-response').textContent = String(e);
  }
});

// get order
document.getElementById('get-order').addEventListener('click', async () => {
  const id = document.getElementById('get-order-id').value;
  if (!id) return;
  try {
    const res = await fetch(`${API_ORDERS}/${encodeURIComponent(id)}`);
    if (res.status === 404) {
      document.getElementById('get-order-response').textContent = 'Not found';
      return;
    }
    const json = await res.json();
    document.getElementById('get-order-response').textContent = JSON.stringify(json, null, 2);
  } catch (e) {
    document.getElementById('get-order-response').textContent = String(e);
  }
});

// refresh orders list
document.getElementById('refresh-orders').addEventListener('click', () => fetchOrdersList());

async function fetchOrdersList() {
  try {
    const res = await fetch(API_ORDERS);
    const json = await res.json();
    const container = document.getElementById('orders-list');
    if (!Array.isArray(json)) { container.textContent = JSON.stringify(json, null, 2); return; }
    if (json.length === 0) { container.textContent = 'No orders'; return; }
    const html = ['<table><thead><tr><th>Order ID</th><th>Status</th><th>Items</th><th>Actions</th></tr></thead><tbody>'];
    for (const o of json) {
      const items = (o.items || []).map(i => `${i.productId} (x${i.quantity})`).join(', ');
      html.push(`<tr><td>${o.orderId}</td><td>${o.status}</td><td>${items}</td><td><button data-order="${o.orderId}" class="view-order">View</button> <button data-order="${o.orderId}" class="cancel-order-inline">Cancel</button></td></tr>`);
    }
    html.push('</tbody></table>');
    container.innerHTML = html.join('');
    container.querySelectorAll('.view-order').forEach(b => b.addEventListener('click', ev => {
      const id = ev.target.dataset.order; document.getElementById('get-order-id').value = id; document.getElementById('get-order').click();
    }));
    container.querySelectorAll('.cancel-order-inline').forEach(b => b.addEventListener('click', async ev => {
      const id = ev.target.dataset.order; try { const res = await fetch(`${API_ORDERS}/${encodeURIComponent(id)}/cancel`, { method: 'POST' }); const json = await res.json(); alert('Cancelled: ' + JSON.stringify(json)); fetchOrdersList(); } catch(e) { alert(String(e)); }
    }));
    // notify about failed orders (once)
    json.forEach(o => { if (o.status === 'FAILED') notifyOrderFailed(o.orderId); });
  } catch (e) { document.getElementById('orders-list').textContent = String(e); }
}

// Poll order status until it changes from CREATED or timeout
function pollOrderStatus(orderId, onUpdate, intervalMs = 1500, timeoutMs = 30000) {
  let elapsed = 0;
  const iv = setInterval(async () => {
    try {
      const res = await fetch(`${API_ORDERS}/${encodeURIComponent(orderId)}`);
      if (res.status === 404) { onUpdate({ error: 'not found' }); return; }
      const json = await res.json();
      onUpdate(json);
      if (!json.status || json.status !== 'CREATED') {
        clearInterval(iv);
      }
    } catch (e) {
      onUpdate({ error: String(e) });
    }
    elapsed += intervalMs;
    if (elapsed >= timeoutMs) { clearInterval(iv); onUpdate({ timeout: true }); }
  }, intervalMs);
  return () => clearInterval(iv);
}
// cancel order
document.getElementById('cancel-order').addEventListener('click', async () => {
  const id = document.getElementById('cancel-order-id').value;
  if (!id) return;
  try {
    const res = await fetch(`${API_ORDERS}/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
    const json = await res.json();
    document.getElementById('cancel-order-response').textContent = JSON.stringify(json, null, 2);
  } catch (e) {
    document.getElementById('cancel-order-response').textContent = String(e);
  }
});

// inventory actions
async function inventoryAction(method, url, body) {
  const opts = { method, headers: {} };
  if (body) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
  const res = await fetch(url, opts);
  if (!res.ok) throw new Error(`Status ${res.status}`);
  return res.json();
}

document.getElementById('inv-create').addEventListener('click', async () => {
  const productId = document.getElementById('inv-productId').value;
  const quantity = parseInt(document.getElementById('inv-quantity').value || '0', 10);
  try {
    const json = await inventoryAction('POST', API_INVENTORY, { productId, quantity });
    document.getElementById('inv-response').textContent = JSON.stringify(json, null, 2);
  } catch (e) {
    document.getElementById('inv-response').textContent = String(e);
  }
});

document.getElementById('inv-set').addEventListener('click', async () => {
  const productId = document.getElementById('inv-productId').value;
  const quantity = parseInt(document.getElementById('inv-quantity').value || '0', 10);
  try {
    const json = await inventoryAction('PUT', `${API_INVENTORY}/${encodeURIComponent(productId)}`, { quantity });
    document.getElementById('inv-response').textContent = JSON.stringify(json, null, 2);
  } catch (e) {
    document.getElementById('inv-response').textContent = String(e);
  }
});

document.getElementById('inv-adjust').addEventListener('click', async () => {
  const productId = document.getElementById('inv-productId').value;
  const quantity = parseInt(document.getElementById('inv-quantity').value || '0', 10);
  try {
    const json = await inventoryAction('PATCH', `${API_INVENTORY}/${encodeURIComponent(productId)}/adjust`, { quantity });
    document.getElementById('inv-response').textContent = JSON.stringify(json, null, 2);
  } catch (e) {
    document.getElementById('inv-response').textContent = String(e);
  }
});

// refresh inventory list
document.getElementById('refresh-inventory').addEventListener('click', () => fetchInventoryList());

async function fetchInventoryList() {
  try {
    const res = await fetch(API_INVENTORY);
    const json = await res.json();
    const container = document.getElementById('inventory-list');
    if (!Array.isArray(json)) { container.textContent = JSON.stringify(json, null, 2); return; }
    if (json.length === 0) { container.textContent = 'No inventory'; return; }
    const rows = json.map(it => `
      <tr data-product="${it.productId}">
        <td>${it.productId}</td>
        <td class="qty">${it.availableQuantity}</td>
        <td><button class="edit-inv">Edit</button></td>
      </tr>
    `).join('');
    container.innerHTML = `<table><thead><tr><th>Product</th><th>Quantity</th><th>Actions</th></tr></thead><tbody>${rows}</tbody></table>`;
    container.querySelectorAll('.edit-inv').forEach(b => b.addEventListener('click', ev => {
      const tr = ev.target.closest('tr'); const prod = tr.dataset.product; const qtyCell = tr.querySelector('.qty'); const cur = qtyCell.textContent;
      qtyCell.innerHTML = `<input class="edit-qty" value="${cur}" type="number" min="0">`;
      ev.target.textContent = 'Save'; ev.target.classList.remove('edit-inv'); ev.target.classList.add('save-inv');
      ev.target.removeEventListener('click', arguments.callee);
      ev.target.addEventListener('click', async e2 => {
        const newQty = parseInt(tr.querySelector('.edit-qty').value || '0', 10);
        try {
          const json = await inventoryAction('PUT', `${API_INVENTORY}/${encodeURIComponent(prod)}`, { quantity: newQty });
          alert('Saved'); fetchInventoryList();
        } catch (err) { alert(String(err)); }
      });
    }));
  } catch (e) { document.getElementById('inventory-list').textContent = String(e); }
}

// auto-load lists on first show
fetchOrdersList();
fetchInventoryList();

// --- toasts / notifications ---
const notifiedFailed = new Set();

function showToast(message, type = 'error', ttl = 5000) {
  const container = document.getElementById('toast-container');
  if (!container) return;
  const t = document.createElement('div');
  t.className = 'toast ' + (type === 'success' ? 'toast-success' : 'toast-error');
  t.textContent = message;
  container.appendChild(t);
  const remove = () => { t.style.opacity = 0; setTimeout(() => t.remove(), 300); };
  setTimeout(remove, ttl);
}

function notifyOrderFailed(orderId) {
  if (notifiedFailed.has(orderId)) return;
  notifiedFailed.add(orderId);
  showToast(`Order ${orderId} has failed`, 'error', 6000);
}

// make poll also notify on failure
function pollOrderStatus(orderId, onUpdate, intervalMs = 1500, timeoutMs = 30000) {
  let elapsed = 0;
  const iv = setInterval(async () => {
    try {
      const res = await fetch(`${API_ORDERS}/${encodeURIComponent(orderId)}`);
      if (res.status === 404) { onUpdate({ error: 'not found' }); return; }
      const json = await res.json();
      onUpdate(json);
      if (json.status === 'FAILED') {
        notifyOrderFailed(json.orderId);
      }
      if (!json.status || json.status !== 'CREATED') {
        clearInterval(iv);
      }
    } catch (e) {
      onUpdate({ error: String(e) });
    }
    elapsed += intervalMs;
    if (elapsed >= timeoutMs) { clearInterval(iv); onUpdate({ timeout: true }); }
  }, intervalMs);
  return () => clearInterval(iv);
}

// small helper to pre-wire the first remove button (if any)
document.querySelectorAll('.remove-item').forEach(b => b.addEventListener('click', ev => ev.target.closest('.item-row').remove()));
