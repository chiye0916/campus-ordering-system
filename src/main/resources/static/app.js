const state = {
  token: localStorage.getItem("demo3_token") || "",
  user: JSON.parse(localStorage.getItem("demo3_user") || "null"),
  categories: [],
  dishes: [],
  cart: [],
  orders: [],
  selectedCategoryId: null
};

const $ = (selector) => document.querySelector(selector);
const money = (value) => `¥${Number(value || 0).toFixed(2)}`;

function showToast(message) {
  $("#toast").textContent = message;
}

async function api(path, options = {}) {
  const headers = options.headers || {};
  if (options.body) {
    headers["Content-Type"] = "application/json";
  }
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }

  const response = await fetch(path, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.message || "请求失败");
  }
  return result.data;
}

function requireLogin() {
  if (!state.token) {
    throw new Error("请先登录");
  }
}

function setSession(loginVO) {
  state.token = loginVO.token;
  state.user = {
    id: loginVO.id,
    username: loginVO.username,
    nickname: loginVO.nickname,
    role: loginVO.role
  };
  localStorage.setItem("demo3_token", state.token);
  localStorage.setItem("demo3_user", JSON.stringify(state.user));
  renderSession();
}

function clearSession() {
  state.token = "";
  state.user = null;
  localStorage.removeItem("demo3_token");
  localStorage.removeItem("demo3_user");
  renderSession();
}

function renderSession() {
  const loggedIn = Boolean(state.token && state.user);
  const isAdmin = loggedIn && state.user.role === "ADMIN";
  $("#guestPanel").classList.toggle("hidden", loggedIn);
  $("#userPanel").classList.toggle("hidden", !loggedIn);
  document.querySelector('[data-view="manageView"]').classList.toggle("hidden", !isAdmin);
  if (loggedIn) {
    $("#currentUser").textContent = `${state.user.nickname || state.user.username} · ${state.user.role}`;
  }
  if (!isAdmin && $("#manageView").classList.contains("active")) {
    switchView("menuView");
  }
}

function renderCategories() {
  const tabs = $("#categoryTabs");
  const manageList = $("#manageCategoryList");
  const dishCategoryInput = $("#dishCategoryInput");

  tabs.innerHTML = "";
  manageList.innerHTML = "";
  dishCategoryInput.innerHTML = "";

  state.categories.forEach((category) => {
    const tab = document.createElement("button");
    tab.className = `tab-button ${category.id === state.selectedCategoryId ? "active" : ""}`;
    tab.textContent = category.name;
    tab.onclick = () => selectCategory(category.id);
    tabs.appendChild(tab);

    const option = document.createElement("option");
    option.value = category.id;
    option.textContent = category.name;
    dishCategoryInput.appendChild(option);

    const row = document.createElement("div");
    row.className = "row-item";
    row.innerHTML = `
      <div class="row-main">
        <div>
          <strong>${category.name}</strong>
          <p class="muted-label">排序 ${category.sort}</p>
        </div>
        <div class="row-actions">
          <button class="mini-button" data-action="edit-category" data-id="${category.id}">修改</button>
          <button class="mini-button" data-action="delete-category" data-id="${category.id}">删除</button>
        </div>
      </div>
    `;
    manageList.appendChild(row);
  });

  if (!state.categories.length) {
    tabs.innerHTML = `<div class="empty-state">暂无分类</div>`;
    manageList.innerHTML = `<div class="empty-state">暂无分类</div>`;
  }
}

function renderDishes() {
  const list = $("#dishList");
  list.innerHTML = "";
  state.dishes.forEach((dish) => {
    const card = document.createElement("article");
    card.className = "dish-card";
    card.innerHTML = `
      <div class="dish-card-top">
        <div>
          <h4 class="dish-name">${dish.name}</h4>
          <p class="muted-label">${dish.categoryName || ""}</p>
        </div>
        <span class="status-pill ${dish.status === 1 ? "" : "off"}">${dish.status === 1 ? "上架" : "下架"}</span>
      </div>
      <p>${dish.description || "暂无描述"}</p>
      <div class="row-main">
        <span class="price">${money(dish.price)}</span>
        <button class="primary-button" data-action="add-cart" data-id="${dish.id}">加入购物车</button>
      </div>
    `;
    list.appendChild(card);
  });

  if (!state.dishes.length) {
    list.innerHTML = `<div class="empty-state">当前分类暂无上架商品</div>`;
  }
}

function renderCart() {
  const list = $("#cartList");
  list.innerHTML = "";
  let total = 0;

  state.cart.forEach((item) => {
    total += Number(item.amount || 0);
    const row = document.createElement("div");
    row.className = "cart-item";
    row.innerHTML = `
      <div class="cart-line">
        <div>
          <strong>${item.dishName}</strong>
          <p class="muted-label">${money(item.dishPrice)} × ${item.quantity}</p>
        </div>
        <span class="price">${money(item.amount)}</span>
      </div>
      <div class="row-actions">
        <button class="mini-button" data-action="cart-minus" data-id="${item.dishId}" data-quantity="${item.quantity}">-</button>
        <button class="mini-button" data-action="cart-plus" data-id="${item.dishId}" data-quantity="${item.quantity}">+</button>
      </div>
    `;
    list.appendChild(row);
  });

  if (!state.cart.length) {
    list.innerHTML = `<div class="empty-state">购物车为空</div>`;
  }
  $("#cartTotal").textContent = money(total);
}

function orderStatusText(status) {
  return {
    1: "待支付",
    2: "已支付",
    3: "已完成",
    4: "已取消"
  }[status] || "未知";
}

function renderOrders() {
  const list = $("#orderList");
  list.innerHTML = "";

  state.orders.forEach((order) => {
    const row = document.createElement("div");
    row.className = "row-item";
    row.innerHTML = `
      <div class="row-main">
        <div>
          <strong>${order.number}</strong>
          <p class="muted-label">${order.orderTime || ""} · ${orderStatusText(order.status)}</p>
        </div>
        <span class="price">${money(order.amount)}</span>
      </div>
      <div class="row-actions">
        <button class="mini-button" data-action="order-detail" data-id="${order.id}">详情</button>
        <button class="mini-button" data-action="order-pay" data-id="${order.id}">支付</button>
        <button class="mini-button" data-action="order-cancel" data-id="${order.id}">取消</button>
        <button class="mini-button" data-action="order-complete" data-id="${order.id}">完成</button>
      </div>
    `;
    list.appendChild(row);
  });

  if (!state.orders.length) {
    list.innerHTML = `<div class="empty-state">暂无订单</div>`;
  }
}

function renderOrderDetail(order) {
  $("#orderDetailTitle").textContent = order.number;
  const items = order.items || [];
  $("#orderDetail").innerHTML = `
    <div class="row-item">
      <div class="row-main"><span>状态</span><strong>${orderStatusText(order.status)}</strong></div>
      <div class="row-main"><span>金额</span><strong>${money(order.amount)}</strong></div>
      <div class="row-main"><span>备注</span><strong>${order.remark || "无"}</strong></div>
    </div>
    ${items.map((item) => `
      <div class="row-item">
        <div class="row-main">
          <div>
            <strong>${item.dishName}</strong>
            <p class="muted-label">${money(item.dishPrice)} × ${item.quantity}</p>
          </div>
          <span class="price">${money(item.amount)}</span>
        </div>
      </div>
    `).join("")}
  `;
}

function renderDishPage() {
  const list = $("#manageDishList");
  list.innerHTML = "";

  state.dishes.forEach((dish) => {
    const row = document.createElement("div");
    row.className = "row-item";
    row.innerHTML = `
      <div class="row-main">
        <div>
          <strong>${dish.name}</strong>
          <p class="muted-label">${dish.categoryName || ""} · ${money(dish.price)} · ${dish.status === 1 ? "上架" : "下架"}</p>
        </div>
        <div class="row-actions">
          <button class="mini-button" data-action="edit-dish" data-id="${dish.id}">修改</button>
          <button class="mini-button" data-action="toggle-dish" data-id="${dish.id}" data-status="${dish.status}">${dish.status === 1 ? "下架" : "上架"}</button>
        </div>
      </div>
    `;
    list.appendChild(row);
  });

  if (!state.dishes.length) {
    list.innerHTML = `<div class="empty-state">暂无商品</div>`;
  }
}

async function loadCategories() {
  requireLogin();
  state.categories = await api("/category/list");
  if (!state.selectedCategoryId && state.categories.length) {
    state.selectedCategoryId = state.categories[0].id;
  }
  renderCategories();
}

async function selectCategory(categoryId) {
  state.selectedCategoryId = categoryId;
  renderCategories();
  await loadDishList();
}

async function loadDishList() {
  requireLogin();
  if (!state.selectedCategoryId) {
    state.dishes = [];
  } else {
    state.dishes = await api(`/dish/list?categoryId=${state.selectedCategoryId}`);
  }
  renderDishes();
  renderDishPage();
}

async function loadDishPage() {
  requireLogin();
  const page = await api("/dish/page?page=1&pageSize=50");
  state.dishes = page.records || [];
  renderDishPage();
}

async function loadCart() {
  requireLogin();
  state.cart = await api("/cart/list");
  renderCart();
}

async function loadOrders() {
  requireLogin();
  const page = await api("/order/page?page=1&pageSize=20");
  state.orders = page.records || [];
  renderOrders();
}

async function refreshMenu() {
  await loadCategories();
  await loadDishList();
  await loadCart();
}

async function startApp() {
  renderSession();
  if (!state.token) {
    showToast("请登录");
    return;
  }
  try {
    await refreshMenu();
    await loadOrders();
    showToast("已同步");
  } catch (error) {
    showToast(error.message);
  }
}

function bindNavigation() {
  document.querySelectorAll(".nav-button").forEach((button) => {
    button.addEventListener("click", async () => {
      if (button.dataset.view === "manageView" && state.user?.role !== "ADMIN") {
        showToast("无管理员权限");
        return;
      }
      switchView(button.dataset.view);
      try {
        if (button.dataset.view === "orderView") await loadOrders();
        if (button.dataset.view === "manageView") {
          await loadCategories();
          await loadDishPage();
        }
      } catch (error) {
        showToast(error.message);
      }
    });
  });
}

function switchView(viewId) {
  document.querySelectorAll(".nav-button").forEach((item) => {
    item.classList.toggle("active", item.dataset.view === viewId);
  });
  document.querySelectorAll(".view").forEach((item) => item.classList.remove("active"));
  $(`#${viewId}`).classList.add("active");
  const activeButton = document.querySelector(`[data-view="${viewId}"]`);
  $("#viewTitle").textContent = activeButton ? activeButton.textContent : "点餐";
}

function bindEvents() {
  $("#loginBtn").onclick = async () => {
    try {
      const data = await api("/user/login", {
        method: "POST",
        body: {
          username: $("#usernameInput").value.trim(),
          password: $("#passwordInput").value
        }
      });
      setSession(data);
      await startApp();
      showToast("登录成功");
    } catch (error) {
      showToast(error.message);
    }
  };

  $("#registerBtn").onclick = async () => {
    try {
      await api("/user/register", {
        method: "POST",
        body: {
          username: $("#usernameInput").value.trim(),
          password: $("#passwordInput").value,
          nickname: $("#nicknameInput").value.trim()
        }
      });
      showToast("注册成功，可以登录");
    } catch (error) {
      showToast(error.message);
    }
  };

  $("#logoutBtn").onclick = async () => {
    try {
      await api("/user/logout", { method: "POST" });
    } catch (error) {
      showToast(error.message);
    }
    clearSession();
    showToast("已退出");
  };

  $("#refreshMenuBtn").onclick = async () => {
    try {
      await refreshMenu();
      showToast("菜单已刷新");
    } catch (error) {
      showToast(error.message);
    }
  };

  $("#cleanCartBtn").onclick = async () => {
    try {
      await api("/cart/clean", { method: "DELETE" });
      await loadCart();
      showToast("购物车已清空");
    } catch (error) {
      showToast(error.message);
    }
  };

  $("#submitOrderBtn").onclick = async () => {
    try {
      const orderId = await api("/order/submit", {
        method: "POST",
        body: { remark: $("#remarkInput").value.trim() }
      });
      $("#remarkInput").value = "";
      await loadCart();
      await loadOrders();
      showToast(`订单已提交：${orderId}`);
    } catch (error) {
      showToast(error.message);
    }
  };

  $("#refreshOrdersBtn").onclick = async () => {
    try {
      await loadOrders();
      showToast("订单已刷新");
    } catch (error) {
      showToast(error.message);
    }
  };

  $("#refreshDishPageBtn").onclick = async () => {
    try {
      await loadDishPage();
      showToast("商品已刷新");
    } catch (error) {
      showToast(error.message);
    }
  };

  $("#categoryForm").onsubmit = async (event) => {
    event.preventDefault();
    try {
      await api("/category", {
        method: "POST",
        body: {
          name: $("#categoryNameInput").value.trim(),
          sort: Number($("#categorySortInput").value)
        }
      });
      event.target.reset();
      await loadCategories();
      showToast("分类已新增");
    } catch (error) {
      showToast(error.message);
    }
  };

  $("#dishForm").onsubmit = async (event) => {
    event.preventDefault();
    try {
      await api("/dish", {
        method: "POST",
        body: {
          categoryId: Number($("#dishCategoryInput").value),
          name: $("#dishNameInput").value.trim(),
          price: Number($("#dishPriceInput").value),
          image: $("#dishImageInput").value.trim(),
          description: $("#dishDescriptionInput").value.trim(),
          status: Number($("#dishStatusInput").value)
        }
      });
      event.target.reset();
      await loadDishPage();
      await loadDishList();
      showToast("商品已新增");
    } catch (error) {
      showToast(error.message);
    }
  };

  document.body.addEventListener("click", async (event) => {
    const target = event.target.closest("[data-action]");
    if (!target) return;
    const action = target.dataset.action;
    const id = Number(target.dataset.id);

    try {
      if (action === "add-cart") {
        await api("/cart/add", { method: "POST", body: { dishId: id, quantity: 1 } });
        await loadCart();
        showToast("已加入购物车");
      }

      if (action === "cart-minus" || action === "cart-plus") {
        const oldQuantity = Number(target.dataset.quantity);
        const quantity = action === "cart-plus" ? oldQuantity + 1 : oldQuantity - 1;
        if (quantity < 1) {
          await api(`/cart/${id}`, { method: "DELETE" });
          await loadCart();
          showToast("商品已移出购物车");
        } else {
          await api("/cart/update", { method: "PUT", body: { dishId: id, quantity } });
          await loadCart();
        }
      }

      if (action === "order-detail") {
        const order = await api(`/order/${id}`);
        renderOrderDetail(order);
      }

      if (action === "order-pay") {
        const payResult = await api(`/order/${id}/pay`, { method: "PUT" });
        await loadOrders();
        showToast(`订单已支付：${money(payResult.amount)}`);
      }

      if (action === "order-cancel") {
        await api(`/order/${id}/cancel`, { method: "PUT" });
        await loadOrders();
        showToast("订单已取消");
      }

      if (action === "order-complete") {
        await api(`/order/${id}/complete`, { method: "PUT" });
        await loadOrders();
        showToast("订单已完成");
      }

      if (action === "edit-category") {
        const category = state.categories.find((item) => item.id === id);
        const name = prompt("分类名称", category.name);
        const sort = prompt("排序", category.sort);
        if (name && sort !== null) {
          await api(`/category/${id}`, {
            method: "PUT",
            body: { name: name.trim(), sort: Number(sort) }
          });
          await loadCategories();
          showToast("分类已修改");
        }
      }

      if (action === "delete-category") {
        await api(`/category/${id}`, { method: "DELETE" });
        state.selectedCategoryId = null;
        await loadCategories();
        await loadDishList();
        showToast("分类已删除");
      }

      if (action === "toggle-dish") {
        const status = Number(target.dataset.status) === 1 ? 0 : 1;
        await api(`/dish/${id}/status`, { method: "PUT", body: { status } });
        await loadDishPage();
        await loadDishList();
        showToast(status === 1 ? "商品已上架" : "商品已下架");
      }

      if (action === "edit-dish") {
        const dish = state.dishes.find((item) => item.id === id);
        const name = prompt("商品名称", dish.name);
        const price = prompt("价格", dish.price);
        if (name && price) {
          await api(`/dish/${id}`, {
            method: "PUT",
            body: {
              categoryId: dish.categoryId,
              name: name.trim(),
              price: Number(price),
              image: dish.image || "",
              description: dish.description || "",
              status: dish.status
            }
          });
          await loadDishPage();
          await loadDishList();
          showToast("商品已修改");
        }
      }
    } catch (error) {
      showToast(error.message);
    }
  });
}

bindNavigation();
bindEvents();
startApp();
