document.addEventListener('DOMContentLoaded', async () => {
    await checkAuth();
    renderSidebar();
});

async function checkAuth() {
    const sessionStr = localStorage.getItem('carelyo_admin_session');
    if (!sessionStr) {
        window.location.href = 'login.html';
        return;
    }

    const session = JSON.parse(sessionStr);
    const allowedRoles = ['admin', 'nurse', 'doctor'];
    const userRole = session.role ? session.role.toLowerCase() : '';

    if (!allowedRoles.includes(userRole)) {
        localStorage.removeItem('carelyo_admin_session');
        alert('Access denied. Only admin, nurse, and doctor roles are allowed.');
        window.location.href = 'login.html';
    }
}

function renderSidebar() {
    const sidebarHtml = `
    <div class="sidebar">
        <div class="sidebar-header">
            <h1 class="brand-title">Carelyo</h1>
            <p class="brand-subtitle">Admin Dashboard</p>
        </div>
        <nav class="sidebar-nav">
            <a href="index.html" class="nav-item" id="nav-dashboard">
                <i class="ph ph-squares-four"></i>
                Dashboard
            </a>
            <a href="parents.html" class="nav-item" id="nav-parents">
                <i class="ph ph-users"></i>
                Parent Accounts
            </a>
            <a href="children.html" class="nav-item" id="nav-children">
                <i class="ph ph-baby"></i>
                Child Accounts
            </a>
            <a href="appointments.html" class="nav-item" id="nav-appointments">
                <i class="ph ph-calendar-blank"></i>
                Appointments
            </a>
            <a href="health.html" class="nav-item" id="nav-health">
                <i class="ph ph-heart"></i>
                Health Records
            </a>
            <a href="vaccinations.html" class="nav-item" id="nav-vaccinations">
                <i class="ph ph-syringe"></i>
                Vaccination Status
            </a>
        </nav>
        <div class="sidebar-footer">
            <button class="logout-btn" onclick="logout()">
                <i class="ph ph-sign-out"></i>
                Logout
            </button>
        </div>
    </div>
    `;

    // Insert sidebar at the beginning of body
    document.body.insertAdjacentHTML('afterbegin', sidebarHtml);

    // Set active class based on current URL
    const currentPath = window.location.pathname;
    const pageName = currentPath.split('/').pop() || 'index.html';
    
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('href') === pageName) {
            item.classList.add('active');
        }
    });
}

async function logout() {
    if (confirm('Are you sure you want to log out?')) {
        localStorage.removeItem('carelyo_admin_session');
        window.location.href = 'login.html';
    }
}

// Utility function to format dates
function formatDate(dateString) {
    if (!dateString) return 'N/A';
    const options = { year: 'numeric', month: 'short', day: 'numeric' };
    return new Date(dateString).toLocaleDateString(undefined, options);
}
