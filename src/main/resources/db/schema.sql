-- Simple schema for demo; JPA will manage schema when ddl-auto=update is set
CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id VARCHAR(255),
  status VARCHAR(20),
  created_at DATETIME
);

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT,
  product_id VARCHAR(255),
  quantity INT,
  FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE IF NOT EXISTS inventory (
  product_id VARCHAR(255) PRIMARY KEY,
  available_quantity INT,
  version BIGINT
);

CREATE TABLE IF NOT EXISTS dead_letter_events (
  event_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payload TEXT,
  error_message TEXT,
  retry_count INT,
  created_at DATETIME
);
