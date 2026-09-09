document.addEventListener('DOMContentLoaded', async () => {
    await checkAuth();
    await renderSidebar();
});

async function checkAuth() {
    const sessionStr = localStorage.getItem('carelyo_admin_session');
    if (!sessionStr) {
        window.location.href = 'login.html';
        return;
    }

    const session = JSON.parse(sessionStr);
    const userRole = session.role ? session.role.toLowerCase() : '';

    if (userRole !== 'staff') {
        localStorage.removeItem('carelyo_admin_session');
        alert('Access denied. Only staff are allowed. Parents are not authorized.');
        window.location.href = 'login.html';
    }
}

function getInitials(name) {
    if (!name) return 'ST';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
}

async function renderSidebar() {
    const sessionStr = localStorage.getItem('carelyo_admin_session');
    let session = sessionStr ? JSON.parse(sessionStr) : {};
    
    // Fetch clinic details if not cached yet or if only partial info
    if (session.userid && (!session.clinic || !session.clinic.clinic_name || !session.clinic.address)) {
        try {
            if (window.supabaseClient) {
                // 1. Look up CLINIC_STAFF for this staff user
                const { data: staffData } = await window.supabaseClient
                    .from('CLINIC_STAFF')
                    .select('*')
                    .eq('userid', session.userid)
                    .maybeSingle();

                const targetClinicId = (staffData && staffData.clinicid) ? staffData.clinicid : session.clinicid;

                if (targetClinicId) {
                    session.clinicid = targetClinicId;
                    if (staffData && staffData.role_at_clinic) {
                        session.role_at_clinic = staffData.role_at_clinic;
                    }

                    // 2. Look up the specific clinic from CLINIC table
                    const { data: clinicData } = await window.supabaseClient
                        .from('CLINIC')
                        .select('*')
                        .eq('clinicid', targetClinicId)
                        .maybeSingle();

                    if (clinicData) {
                        session.clinic = clinicData;
                        session.clinic_name = clinicData.clinic_name;
                        localStorage.setItem('carelyo_admin_session', JSON.stringify(session));
                    }
                }
            }
        } catch (e) {
            console.error("Error fetching clinic in sidebar:", e);
        }
    }

    const clinic = session.clinic || {};
    const clinicName = clinic.clinic_name || session.clinic_name || 'Klinik Kesihatan';
    const clinicAddress = clinic.address || 'Address not specified';
    const clinicPhone = clinic.phone_number || '03-4142 5678';
    const clinicEmail = clinic.email || 'clinic@moh.gov.my';
    
    const staffName = session.full_name || 'Staff Member';
    const staffInitials = getInitials(staffName);

    const sidebarHtml = `
    <div class="sidebar">
        <div class="sidebar-header">
            <div class="brand-box">
                <div class="brand-logo-icon">
                    <i class="ph ph-activity"></i>
                </div>
                <h1 class="brand-title">Carelyo</h1>
            </div>
            
            <div class="sidebar-clinic-box">
                <div class="clinic-title-sidebar">${clinicName}</div>
                <div class="clinic-address-sidebar">
                    <i class="ph ph-map-pin"></i>
                    <span>${clinicAddress}</span>
                </div>
            </div>
        </div>

        <nav class="sidebar-nav">
            <a href="index.html" class="nav-item" id="nav-dashboard">
                <i class="ph ph-squares-four"></i>
                <span>Dashboard</span>
            </a>
            <a href="appointments.html" class="nav-item" id="nav-appointments">
                <i class="ph ph-calendar-blank"></i>
                <span>Appointments</span>
            </a>
            <a href="parents.html" class="nav-item" id="nav-parents">
                <i class="ph ph-users"></i>
                <span>Parents</span>
            </a>
            <a href="vaccinations.html" class="nav-item" id="nav-vaccinations">
                <i class="ph ph-shield"></i>
                <span>Vaccinations</span>
            </a>
            <a href="notifications.html" class="nav-item" id="nav-notifications">
                <i class="ph ph-chat-teardrop-dots"></i>
                <span>Notifications</span>
            </a>
        </nav>

        <div class="sidebar-footer">
            <div class="user-profile-row">
                <div class="user-avatar-circle">${staffInitials}</div>
                <div class="user-profile-name" title="${staffName}">${staffName}</div>
            </div>

            <div class="clinic-contact-info">
                <div class="contact-item" title="${clinicPhone}">
                    <i class="ph ph-phone"></i>
                    <span>${clinicPhone}</span>
                </div>
                <div class="contact-item" title="${clinicEmail}">
                    <i class="ph ph-envelope"></i>
                    <span>${clinicEmail}</span>
                </div>
            </div>

            <button class="logout-btn-clean" onclick="logout()">
                <i class="ph ph-sign-out"></i>
                <span>Log Out</span>
            </button>
        </div>
    </div>
    `;

    const existingSidebar = document.querySelector('.sidebar');
    if (existingSidebar) existingSidebar.remove();

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
