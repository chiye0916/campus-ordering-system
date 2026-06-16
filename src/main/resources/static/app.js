const state = {
  token: localStorage.getItem("demo3_token") || "",
  user: JSON.parse(localStorage.getItem("demo3_user") || "null"),
  categories: [],
  menuDishes: [],
  adminDishes: [],
  cart: [],
  orders: [],
  selectedCategoryId: null,
  activeView: "homeView"
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const money = (value) => `¥${Number(value || 0).toFixed(2)}`;

const statusText = {
  1: "待支付",
  2: "已支付",
  3: "已完成",
  4: "已取消"
};

const statusClass = {
  1: "warning-button",
  2: "success-button",
  3: "button-secondary",
  4: "danger-button"
};

function showToast(message, type = "info") {
  const toast = document.createElement("div");
  toast.className = `toast ${type}`;
  toast.textContent = message;
  $("#toastStack").appendChild(toast);
  setTimeout(() => toast.remove(), 3200);
}

function showLoading(container, text = "正在加载数据...") {
  container.innerHTML = `<div class="loading-state">${text}</div>`;
}

function showEmpty(container, text) {
  container.innerHTML = `<div class="empty-state">${text}</div>`;
}

function showError(container, text) {
  container.innerHTML = `<div class="error-state">${text}</div>`;
}

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }

  let response;
  try {
    response = await fetch(path, {
      method: options.method || "GET",
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined
    });
  } catch (error) {
    throw new Error("网络请求失败，请确认后端服务已启动");
  }

  let result;
  try {
    result = await response.json();
  } catch (error) {
    throw new Error("服务响应格式异常");
  }

  if (result.code !== 200) {
    throw new Error(result.message || "请求失败");
  }
  return result.data;
}

function requireLogin() {
  if (!state.token || !state.user) {
    throw new Error("请先登录");
  }
}

function isAdmin() {
  return state.user?.role === "ADMIN";
}

function setSession(loginVO) {
  state.token = loginVO.token;
  state.user = {
    id: loginVO.id,
    username: loginVO.username,
    nickname: loginVO.nickname,
    role: loginVO.role
  };

  if ($("#rememberInput").checked) {
    localStorage.setItem("demo3_token", state.token);
    localStorage.setItem("demo3_user", JSON.stringify(state.user));
  }
  renderSession();
}

function clearSession() {
  state.token = "";
  state.user = null;
  state.categories = [];
  state.menuDishes = [];
  state.adminDishes = [];
  state.cart = [];
  state.orders = [];
  state.selectedCategoryId = null;
  localStorage.removeItem("demo3_token");
  localStorage.removeItem("demo3_user");
  renderSession();
}

function renderSession() {
  const loggedIn = Boolean(state.token && state.user);
  $("#loginPage").classList.toggle("hidden", loggedIn);
  $("#appPage").classList.toggle("hidden", !loggedIn);
  $("#adminNavBtn").classList.toggle("hidden", !isAdmin());

  if (!loggedIn) {
    return;
  }

  const name = state.user.nickname || state.user.username;
  $("#currentUserName").textContent = name;
  $("#currentUserRole").textContent = state.user.role;
  $("#userInitial").textContent = name.slice(0, 1).toUpperCase();

  if (!isAdmin() && state.activeView === "adminView") {
    switchView("homeView");
  }
}

function switchView(viewId) {
  if (viewId === "adminView" && !isAdmin()) {
    showToast("无管理员权限", "error");
    return;
  }
  state.activeView = viewId;
  $$(".nav-link").forEach((button) => {
    button.classList.toggle("active", button.dataset.view === viewId);
  });
  $$(".view").forEach((view) => view.classList.toggle("active", view.id === viewId));
}

function openModal({ title, body, confirmText = "确认", cancelText = "取消", danger = false, onConfirm }) {
  return new Promise((resolve) => {
    const root = $("#modalRoot");
    root.classList.remove("hidden");
    root.innerHTML = `
      <div class="modal-card" role="dialog" aria-modal="true">
        <h3>${title}</h3>
        <div class="modal-body">${body}</div>
        <div class="modal-actions">
          <button class="button button-secondary" type="button" data-modal-cancel>${cancelText}</button>
          <button class="button ${danger ? "danger-button" : "button-primary"}" type="button" data-modal-confirm>${confirmText}</button>
        </div>
      </div>
    `;

    const close = (result) => {
      root.classList.add("hidden");
      root.innerHTML = "";
      resolve(result);
    };

    root.querySelector("[data-modal-cancel]").onclick = () => close(false);
    root.querySelector("[data-modal-confirm]").onclick = async () => {
      if (onConfirm) {
        const result = await onConfirm(root);
        if (result === false) return;
      }
      close(true);
    };
    root.onclick = (event) => {
      if (event.target === root) close(false);
    };
  });
}

function confirmDialog(message, options = {}) {
  return openModal({
    title: options.title || "请确认",
    body: `<p class="muted">${message}</p>`,
    confirmText: options.confirmText || "确认",
    danger: options.danger || false
  });
}

function productInitial(name) {
  return (name || "餐").slice(0, 1);
}

function renderHomeMetrics() {
  const total = state.orders.length;
  const pending = state.orders.filter((order) => order.status === 1 || order.status === 2).length;
  const amount = state.orders.reduce((sum, order) => sum + Number(order.amount || 0), 0);
  const cartCount = state.cart.reduce((sum, item) => sum + Number(item.quantity || 0), 0);

  $("#homeMetrics").innerHTML = [
    ["我的订单", total],
    ["待处理", pending],
    ["累计金额", money(amount)],
    ["购物车餐品", cartCount]
  ].map(([label, value]) => `
    <article class="metric-card">
      <span>${label}</span>
      <strong>${value}</strong>
    </article>
  `).join("");
}

function renderDashboard() {
  const total = state.orders.length;
  const amount = state.orders.reduce((sum, order) => sum + Number(order.amount || 0), 0);
  const pending = state.orders.filter((order) => order.status === 1 || order.status === 2).length;
  const paid = state.orders.filter((order) => order.status === 2).length;

  $("#dashboardMetrics").innerHTML = [
    ["今日订单数", total],
    ["今日营业额", money(amount)],
    ["待处理订单", pending],
    ["已支付订单", paid]
  ].map(([label, value]) => `
    <article class="metric-card">
      <span>${label}</span>
      <strong>${value}</strong>
    </article>
  `).join("");

  const hot = state.adminDishes.slice(0, 5);
  if (!hot.length) {
    showEmpty($("#hotDishList"), "暂无热销菜品数据。当前后端还没有销量统计接口，这里先展示菜品列表前 5 条。");
    return;
  }
  $("#hotDishList").innerHTML = hot.map((dish, index) => `
    <div class="table-row">
      <div class="table-row-main">
        <strong>${index + 1}. ${dish.name}</strong>
        <p class="subtext">${dish.categoryName || "未分类"} · ${money(dish.price)}</p>
      </div>
      <span class="status-pill">${dish.status === 1 ? "上架" : "下架"}</span>
    </div>
  `).join("");
}

function renderCategories() {
  const tabs = $("#categoryTabs");
  const manageList = $("#manageCategoryList");
  const options = state.categories.map((category) => `<option value="${category.id}">${category.name}</option>`).join("");

  if (!state.categories.length) {
    showEmpty(tabs, "暂无分类，请管理员先新增分类");
    showEmpty(manageList, "暂无分类");
    return;
  }

  tabs.innerHTML = state.categories.map((category) => `
    <button class="category-tab ${category.id === state.selectedCategoryId ? "active" : ""}" type="button" data-action="select-category" data-id="${category.id}">
      ${category.name}
    </button>
  `).join("");

  manageList.innerHTML = state.categories.map((category) => `
    <div class="table-row">
      <div class="table-row-main">
        <strong>${category.name}</strong>
        <p class="subtext">排序 ${category.sort}</p>
      </div>
      <div class="row-actions">
        <button class="button button-secondary" type="button" data-action="edit-category" data-id="${category.id}">编辑</button>
        <button class="button danger-button" type="button" data-action="delete-category" data-id="${category.id}">删除</button>
      </div>
    </div>
  `).join("");

  state.categoryOptionsHtml = options;
}

function renderDishes() {
  const list = $("#dishList");
  if (!state.menuDishes.length) {
    showEmpty(list, "当前分类暂无上架商品");
    return;
  }

  list.innerHTML = state.menuDishes.map((dish) => `
    <article class="product-card">
      <div class="product-media" style="${dish.image ? `background-image: linear-gradient(135deg, rgba(15,34,54,.40), rgba(15,34,54,.20)), url('${dish.image}')` : ""}">
        ${dish.image ? "" : productInitial(dish.name)}
      </div>
      <div class="product-body">
        <div class="product-title">
          <div>
            <h3>${dish.name}</h3>
            <p class="subtext">${dish.categoryName || "商务精选"} · 适合办公用餐</p>
          </div>
          <span class="status-pill">精选</span>
        </div>
        <p>${dish.description || "品质餐品，适合会议、办公与高效日程。"}</p>
        <div class="product-foot">
          <span class="price">${money(dish.price)}</span>
          <button class="button button-primary" type="button" data-action="add-cart" data-id="${dish.id}">加入购物车</button>
        </div>
      </div>
    </article>
  `).join("");
}

function renderCart() {
  const list = $("#cartList");
  let total = 0;

  if (!state.cart.length) {
    $("#cartTotal").textContent = money(0);
    showEmpty(list, "购物车为空，请先选择餐品");
    renderHomeMetrics();
    return;
  }

  list.innerHTML = state.cart.map((item) => {
    total += Number(item.amount || 0);
    return `
      <div class="cart-item">
        <div class="cart-main">
          <div>
            <strong>${item.dishName}</strong>
            <p class="subtext">${money(item.dishPrice)} × ${item.quantity}</p>
          </div>
          <span class="price">${money(item.amount)}</span>
        </div>
        <div class="quantity-row">
          <div class="quantity-actions">
            <button class="quantity-button" type="button" data-action="cart-minus" data-id="${item.dishId}" data-quantity="${item.quantity}">-</button>
            <strong>${item.quantity}</strong>
            <button class="quantity-button" type="button" data-action="cart-plus" data-id="${item.dishId}" data-quantity="${item.quantity}">+</button>
          </div>
          <button class="text-button" type="button" data-action="cart-delete" data-id="${item.dishId}">删除</button>
        </div>
      </div>
    `;
  }).join("");

  $("#cartTotal").textContent = money(total);
  renderHomeMetrics();
}

function renderOrders(target = $("#orderList"), adminMode = false) {
  if (!state.orders.length) {
    showEmpty(target, adminMode ? "暂无订单数据" : "暂无订单，提交订单后会显示在这里");
    return;
  }

  target.innerHTML = state.orders.map((order) => `
    <div class="table-row">
      <div class="table-row-main">
        <strong>${order.number}</strong>
        <p class="subtext">${order.orderTime || ""} · ${adminMode ? "用户信息接口待补充" : "我的订单"}</p>
      </div>
      <span class="button ${statusClass[order.status] || "button-secondary"}">${statusText[order.status] || "未知"}</span>
      <span class="price">${money(order.amount)}</span>
      <div class="row-actions">
        <button class="button button-secondary" type="button" data-action="order-detail" data-id="${order.id}">查看</button>
        ${order.status === 1 && !adminMode ? `<button class="button success-button" type="button" data-action="order-pay" data-id="${order.id}">支付</button>` : ""}
        ${order.status !== 3 && order.status !== 4 ? `<button class="button danger-button" type="button" data-action="order-cancel" data-id="${order.id}">取消</button>` : ""}
        ${adminMode && order.status === 2 ? `<button class="button success-button" type="button" data-action="order-complete" data-id="${order.id}">完成</button>` : ""}
      </div>
    </div>
  `).join("");
}

function renderOrderDetail(order) {
  $("#orderDetailTitle").textContent = order.number;
  $("#orderDetail").innerHTML = `
    <div class="detail-row"><span>状态</span><strong>${statusText[order.status] || "未知"}</strong></div>
    <div class="detail-row"><span>金额</span><strong>${money(order.amount)}</strong></div>
    <div class="detail-row"><span>备注</span><strong>${order.remark || "无"}</strong></div>
    ${(order.items || []).map((item) => `
      <div class="detail-row">
        <div>
          <strong>${item.dishName}</strong>
          <p class="subtext">${money(item.dishPrice)} × ${item.quantity}</p>
        </div>
        <span class="price">${money(item.amount)}</span>
      </div>
    `).join("")}
  `;
}

function orderDetailHtml(order) {
  return `
    <div class="detail-content">
      <div class="detail-row"><span>状态</span><strong>${statusText[order.status] || "未知"}</strong></div>
      <div class="detail-row"><span>金额</span><strong>${money(order.amount)}</strong></div>
      <div class="detail-row"><span>备注</span><strong>${order.remark || "无"}</strong></div>
      ${(order.items || []).map((item) => `
        <div class="detail-row">
          <div>
            <strong>${item.dishName}</strong>
            <p class="subtext">${money(item.dishPrice)} × ${item.quantity}</p>
          </div>
          <span class="price">${money(item.amount)}</span>
        </div>
      `).join("")}
    </div>
  `;
}

function renderAdminDishes() {
  const list = $("#manageDishList");
  if (!state.adminDishes.length) {
    showEmpty(list, "暂无菜品，请先新增");
    return;
  }

  list.innerHTML = state.adminDishes.map((dish) => `
    <div class="table-row">
      <div class="table-row-main">
        <strong>${dish.name}</strong>
        <p class="subtext">${dish.categoryName || "未分类"} · ${money(dish.price)} · ${dish.description || "暂无描述"}</p>
      </div>
      <span class="status-pill ${dish.status === 1 ? "" : "off"}">${dish.status === 1 ? "上架" : "下架"}</span>
      <div class="row-actions">
        <button class="button button-secondary" type="button" data-action="edit-dish" data-id="${dish.id}">编辑</button>
        <button class="button warning-button" type="button" data-action="toggle-dish" data-id="${dish.id}" data-status="${dish.status}">${dish.status === 1 ? "下架" : "上架"}</button>
        <button class="button danger-button" type="button" data-action="delete-dish" data-id="${dish.id}">删除</button>
      </div>
    </div>
  `).join("");
}

async function loadCategories() {
  requireLogin();
  state.categories = await api("/category/list");
  if (!state.selectedCategoryId && state.categories.length) {
    state.selectedCategoryId = state.categories[0].id;
  }
  renderCategories();
}

async function loadMenuDishes() {
  requireLogin();
  if (!state.selectedCategoryId) {
    state.menuDishes = [];
  } else {
    state.menuDishes = await api(`/dish/list?categoryId=${state.selectedCategoryId}`);
  }
  renderDishes();
}

async function loadAdminDishes() {
  requireLogin();
  if (!isAdmin()) return;
  const page = await api("/dish/page?page=1&pageSize=100");
  state.adminDishes = page.records || [];
  renderAdminDishes();
  renderDashboard();
}

async function loadCart() {
  requireLogin();
  state.cart = await api("/cart/list");
  renderCart();
}

async function loadOrders() {
  requireLogin();
  state.orders = (await api("/order/page?page=1&pageSize=100")).records || [];
  renderOrders($("#orderList"), false);
  if (isAdmin()) {
    renderOrders($("#adminOrderList"), true);
    renderDashboard();
  }
  renderHomeMetrics();
}

async function refreshMenu() {
  showLoading($("#dishList"));
  await loadCategories();
  await loadMenuDishes();
  await loadCart();
}

async function refreshAdmin() {
  if (!isAdmin()) return;
  await loadCategories();
  await loadAdminDishes();
  await loadOrders();
}

async function startApp() {
  renderSession();
  if (!state.token) return;
  try {
    await refreshMenu();
    await loadOrders();
    if (isAdmin()) await loadAdminDishes();
  } catch (error) {
    showToast(error.message, "error");
  }
}

async function submitLogin() {
  const username = $("#usernameInput").value.trim();
  const password = $("#passwordInput").value;

  if (!username) {
    showToast("请输入用户名或手机号", "error");
    return;
  }
  if (!password) {
    showToast("请输入密码", "error");
    return;
  }

  const loginButton = $("#loginForm .button-primary");
  loginButton.disabled = true;
  loginButton.textContent = "登录中...";
  try {
    const data = await api("/user/login", {
      method: "POST",
      body: { username, password }
    });
    setSession(data);
    switchView("homeView");
    await startApp();
    showToast("登录成功", "success");
  } catch (error) {
    showToast(error.message || "用户名或密码错误", "error");
  } finally {
    loginButton.disabled = false;
    loginButton.textContent = "登录";
  }
}

async function logout() {
  try {
    if (state.token) {
      await api("/user/logout", { method: "POST" });
    }
  } catch (error) {
    showToast(error.message, "error");
  }
  clearSession();
  showToast("已退出登录", "success");
}

async function openOrderConfirm() {
  if (!state.cart.length) {
    showToast("购物车为空，请先选择餐品", "error");
    return;
  }

  const address = $("#addressInput").value.trim();
  if (!address) {
    showToast("请填写配送地址", "error");
    return;
  }

  const total = state.cart.reduce((sum, item) => sum + Number(item.amount || 0), 0);
  const remark = $("#remarkInput").value.trim();
  const body = `
    <div class="detail-content">
      ${state.cart.map((item) => `
        <div class="detail-row">
          <div>
            <strong>${item.dishName}</strong>
            <p class="subtext">${money(item.dishPrice)} × ${item.quantity}</p>
          </div>
          <span class="price">${money(item.amount)}</span>
        </div>
      `).join("")}
      <div class="detail-row"><span>配送地址</span><strong>${address}</strong></div>
      <div class="detail-row"><span>备注</span><strong>${remark || "无"}</strong></div>
      <div class="detail-row"><span>总价</span><strong class="price">${money(total)}</strong></div>
    </div>
  `;

  const confirmed = await openModal({
    title: "确认提交订单",
    body,
    confirmText: "提交订单"
  });
  if (!confirmed) return;

  try {
    const orderId = await api("/order/submit", {
      method: "POST",
      body: { remark: `${address}${remark ? `；${remark}` : ""}` }
    });
    $("#remarkInput").value = "";
    await loadCart();
    await loadOrders();
    showToast(`订单提交成功：${orderId}`, "success");
    switchView("ordersView");
  } catch (error) {
    showToast(error.message, "error");
  }
}

async function openCategoryForm(category) {
  const isEdit = Boolean(category);
  await openModal({
    title: isEdit ? "编辑分类" : "新增分类",
    body: `
      <div class="form-grid">
        <label class="field wide">
          <span>分类名称</span>
          <input id="modalCategoryName" type="text" value="${category?.name || ""}" placeholder="例如：商务套餐">
        </label>
        <label class="field wide">
          <span>排序</span>
          <input id="modalCategorySort" type="number" min="0" value="${category?.sort ?? ""}" placeholder="数字越小越靠前">
        </label>
      </div>
    `,
    confirmText: isEdit ? "保存" : "新增",
    onConfirm: async () => {
      const name = $("#modalCategoryName").value.trim();
      const sort = Number($("#modalCategorySort").value);
      if (!name) {
        showToast("请输入分类名称", "error");
        return false;
      }
      if (Number.isNaN(sort)) {
        showToast("请输入排序", "error");
        return false;
      }
      try {
        if (isEdit) {
          await api(`/category/${category.id}`, { method: "PUT", body: { name, sort } });
        } else {
          await api("/category", { method: "POST", body: { name, sort } });
        }
        state.selectedCategoryId = null;
        await loadCategories();
        await loadMenuDishes();
        showToast(isEdit ? "分类已保存" : "分类已新增", "success");
      } catch (error) {
        showToast(error.message, "error");
        return false;
      }
    }
  });
}

async function openDishForm(dish) {
  const isEdit = Boolean(dish);
  const selectedCategory = dish?.categoryId || state.categories[0]?.id || "";
  await openModal({
    title: isEdit ? "编辑菜品" : "新增菜品",
    body: `
      <div class="form-grid">
        <label class="field">
          <span>分类</span>
          <select id="modalDishCategory">${state.categories.map((category) => `
            <option value="${category.id}" ${Number(selectedCategory) === category.id ? "selected" : ""}>${category.name}</option>
          `).join("")}</select>
        </label>
        <label class="field">
          <span>状态</span>
          <select id="modalDishStatus">
            <option value="1" ${dish?.status !== 0 ? "selected" : ""}>上架</option>
            <option value="0" ${dish?.status === 0 ? "selected" : ""}>下架</option>
          </select>
        </label>
        <label class="field">
          <span>菜品名称</span>
          <input id="modalDishName" type="text" value="${dish?.name || ""}" placeholder="例如：香辣鸡腿饭">
        </label>
        <label class="field">
          <span>价格</span>
          <input id="modalDishPrice" type="number" min="0.01" step="0.01" value="${dish?.price || ""}" placeholder="20.00">
        </label>
        <label class="field wide">
          <span>图片地址</span>
          <input id="modalDishImage" type="text" value="${dish?.image || ""}" placeholder="可选，后续接入图片上传">
        </label>
        <label class="field wide">
          <span>描述</span>
          <textarea id="modalDishDescription" rows="3" placeholder="适合商务办公、会议用餐等">${dish?.description || ""}</textarea>
        </label>
      </div>
    `,
    confirmText: isEdit ? "保存" : "新增",
    onConfirm: async () => {
      const payload = {
        categoryId: Number($("#modalDishCategory").value),
        name: $("#modalDishName").value.trim(),
        price: Number($("#modalDishPrice").value),
        image: $("#modalDishImage").value.trim(),
        description: $("#modalDishDescription").value.trim(),
        status: Number($("#modalDishStatus").value)
      };
      if (!payload.categoryId || !payload.name || !payload.price) {
        showToast("请完整填写菜品分类、名称和价格", "error");
        return false;
      }
      try {
        if (isEdit) {
          await api(`/dish/${dish.id}`, { method: "PUT", body: payload });
        } else {
          await api("/dish", { method: "POST", body: payload });
        }
        await loadAdminDishes();
        await loadMenuDishes();
        showToast(isEdit ? "菜品已保存" : "菜品已新增", "success");
      } catch (error) {
        showToast(error.message, "error");
        return false;
      }
    }
  });
}

function bindEvents() {
  $("#loginForm").onsubmit = (event) => {
    event.preventDefault();
    submitLogin();
  };

  $("#logoutBtn").onclick = logout;
  $("#submitOrderBtn").onclick = openOrderConfirm;

  $("#refreshMenuBtn").onclick = async () => {
    try {
      await refreshMenu();
      showToast("菜单已刷新", "success");
    } catch (error) {
      showToast(error.message, "error");
    }
  };

  $("#refreshOrdersBtn").onclick = async () => {
    try {
      await loadOrders();
      showToast("订单已刷新", "success");
    } catch (error) {
      showToast(error.message, "error");
    }
  };

  $("#refreshDashboardBtn").onclick = async () => {
    try {
      await refreshAdmin();
      showToast("数据已刷新", "success");
    } catch (error) {
      showToast(error.message, "error");
    }
  };

  $("#refreshAdminOrdersBtn").onclick = async () => {
    try {
      await loadOrders();
      showToast("订单已刷新", "success");
    } catch (error) {
      showToast(error.message, "error");
    }
  };
  $("#newCategoryBtn").onclick = () => openCategoryForm();
  $("#newDishBtn").onclick = () => openDishForm();

  $("#cleanCartBtn").onclick = async () => {
    if (!state.cart.length) {
      showToast("购物车已经是空的", "error");
      return;
    }
    const confirmed = await confirmDialog("确定清空当前购物车吗？", { danger: true, confirmText: "清空" });
    if (!confirmed) return;
    try {
      await api("/cart/clean", { method: "DELETE" });
      await loadCart();
      showToast("购物车已清空", "success");
    } catch (error) {
      showToast(error.message, "error");
    }
  };

  $$(".nav-link").forEach((button) => {
    button.onclick = async () => {
      switchView(button.dataset.view);
      try {
        if (button.dataset.view === "menuView") await refreshMenu();
        if (button.dataset.view === "ordersView") await loadOrders();
        if (button.dataset.view === "adminView") await refreshAdmin();
      } catch (error) {
        showToast(error.message, "error");
      }
    };
  });

  $$(".admin-menu").forEach((button) => {
    button.onclick = () => {
      $$(".admin-menu").forEach((item) => item.classList.remove("active"));
      $$(".admin-panel").forEach((item) => item.classList.remove("active"));
      button.classList.add("active");
      $(`#${button.dataset.adminTab}`).classList.add("active");
    };
  });

  document.body.addEventListener("click", async (event) => {
    const viewLink = event.target.closest("[data-view-link]");
    if (viewLink) {
      switchView(viewLink.dataset.viewLink);
      if (viewLink.dataset.viewLink === "menuView") await refreshMenu();
      if (viewLink.dataset.viewLink === "ordersView") await loadOrders();
      return;
    }

    const target = event.target.closest("[data-action]");
    if (!target) return;
    const action = target.dataset.action;
    const id = Number(target.dataset.id);

    try {
      if (action === "fill-user") {
        $("#usernameInput").value = "zhangsan";
        $("#passwordInput").value = "123456";
      }

      if (action === "fill-admin") {
        $("#usernameInput").value = "admin";
        $("#passwordInput").value = "123456";
      }

      if (action === "forgot-password") {
        showToast("当前 Demo 暂未接入找回密码流程", "error");
      }

      if (action === "select-category") {
        state.selectedCategoryId = id;
        renderCategories();
        showLoading($("#dishList"));
        await loadMenuDishes();
      }

      if (action === "add-cart") {
        await api("/cart/add", { method: "POST", body: { dishId: id, quantity: 1 } });
        await loadCart();
        showToast("已加入购物车", "success");
      }

      if (action === "cart-plus" || action === "cart-minus") {
        const oldQuantity = Number(target.dataset.quantity);
        const quantity = action === "cart-plus" ? oldQuantity + 1 : oldQuantity - 1;
        if (quantity < 1) {
          const confirmed = await confirmDialog("确定从购物车删除这个商品吗？", { danger: true, confirmText: "删除" });
          if (!confirmed) return;
          await api(`/cart/${id}`, { method: "DELETE" });
          showToast("商品已移出购物车", "success");
        } else {
          await api("/cart/update", { method: "PUT", body: { dishId: id, quantity } });
        }
        await loadCart();
      }

      if (action === "cart-delete") {
        const confirmed = await confirmDialog("确定删除购物车中的这个商品吗？", { danger: true, confirmText: "删除" });
        if (!confirmed) return;
        await api(`/cart/${id}`, { method: "DELETE" });
        await loadCart();
        showToast("商品已删除", "success");
      }

      if (action === "order-detail") {
        const order = await api(`/order/${id}`);
        if (state.activeView === "adminView") {
          await openModal({
            title: `订单 ${order.number}`,
            body: orderDetailHtml(order),
            confirmText: "关闭",
            cancelText: "返回"
          });
        } else {
          renderOrderDetail(order);
          switchView("ordersView");
        }
      }

      if (action === "order-pay") {
        const payResult = await api(`/order/${id}/pay`, { method: "PUT" });
        await loadOrders();
        showToast(`支付成功：${money(payResult.amount)}`, "success");
      }

      if (action === "order-cancel") {
        const confirmed = await confirmDialog("确定取消这笔订单吗？", { danger: true, confirmText: "取消订单" });
        if (!confirmed) return;
        await api(`/order/${id}/cancel`, { method: "PUT" });
        await loadOrders();
        showToast("订单已取消", "success");
      }

      if (action === "order-complete") {
        const confirmed = await confirmDialog("确认将订单标记为已完成吗？", { confirmText: "完成订单" });
        if (!confirmed) return;
        await api(`/order/${id}/complete`, { method: "PUT" });
        await loadOrders();
        showToast("订单已完成", "success");
      }

      if (action === "edit-category") {
        const category = state.categories.find((item) => item.id === id);
        await openCategoryForm(category);
      }

      if (action === "delete-category") {
        const confirmed = await confirmDialog("删除分类可能影响商品归属，确定继续吗？", { danger: true, confirmText: "删除" });
        if (!confirmed) return;
        await api(`/category/${id}`, { method: "DELETE" });
        state.selectedCategoryId = null;
        await loadCategories();
        await loadMenuDishes();
        showToast("分类已删除", "success");
      }

      if (action === "edit-dish") {
        const dish = state.adminDishes.find((item) => item.id === id);
        await openDishForm(dish);
      }

      if (action === "toggle-dish") {
        const status = Number(target.dataset.status) === 1 ? 0 : 1;
        await api(`/dish/${id}/status`, { method: "PUT", body: { status } });
        await loadAdminDishes();
        await loadMenuDishes();
        showToast(status === 1 ? "菜品已上架" : "菜品已下架", "success");
      }

      if (action === "delete-dish") {
        const confirmed = await confirmDialog("当前后端没有删除菜品接口。是否先将该菜品下架？", {
          danger: true,
          confirmText: "下架"
        });
        if (!confirmed) return;
        await api(`/dish/${id}/status`, { method: "PUT", body: { status: 0 } });
        await loadAdminDishes();
        await loadMenuDishes();
        showToast("已用下架替代删除。后续可新增 DELETE /dish/{id} 接口", "success");
      }
    } catch (error) {
      showToast(error.message, "error");
    }
  });
}

bindEvents();
startApp();
