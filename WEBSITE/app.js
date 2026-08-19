document.addEventListener('DOMContentLoaded', () => {
    renderSidebar();
});

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

function logout() {
    alert("Logging out...");
    // Future logic: clear session, redirect to login
}

// Utility function to format dates
function formatDate(dateString) {
    if (!dateString) return 'N/A';
    const options = { year: 'numeric', month: 'short', day: 'numeric' };
    return new Date(dateString).toLocaleDateString(undefined, options);
}
