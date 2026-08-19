document.addEventListener('DOMContentLoaded', () => {
    loadDashboardData();
    initCharts();
});

async function loadDashboardData() {
    try {
        // Fetch Total Parents (assuming role isn't strictly enforced or we count all users)
        const { count: parentsCount, error: parentError } = await window.supabaseClient
            .from('USER')
            .select('*', { count: 'exact', head: true });
        
        if (!parentError) {
            document.getElementById('stat-parents').innerText = parentsCount.toLocaleString();
        }

        // Fetch Children Registered
        const { count: childrenCount, error: childError } = await window.supabaseClient
            .from('CHILD')
            .select('*', { count: 'exact', head: true });
        
        if (!childError) {
            document.getElementById('stat-children').innerText = childrenCount.toLocaleString();
        }

        // Fetch Appointments Today
        const today = new Date().toISOString().split('T')[0];
        const { count: apptCount, error: apptError } = await window.supabaseClient
            .from('APPOINTMENT')
            .select('*', { count: 'exact', head: true })
            .eq('appointment_date', today);
        
        if (!apptError) {
            document.getElementById('stat-appointments').innerText = apptCount.toLocaleString();
        }

        // Fetch Overdue Vaccinations (Mocked for now as complex join logic is needed)
        // In a real scenario, this would query CHILD_VACCINE joined with VACCINATION
        document.getElementById('stat-overdue').innerText = "23";

        // Fetch Recent Activities
        loadRecentActivities();

    } catch (err) {
        console.error("Error loading dashboard data:", err);
    }
}

async function loadRecentActivities() {
    const listContainer = document.getElementById('recent-activities-list');
    
    // We try to fetch from ACTIVITY_LOG. If it fails (table not created yet), we use mock data.
    const { data, error } = await window.supabaseClient
        .from('ACTIVITY_LOG')
        .select('*, USER(full_name)')
        .order('created_at', { ascending: false })
        .limit(5);

    if (error || !data || data.length === 0) {
        // Fallback mock data matching screenshot
        const mockActivities = [
            { title: "New parent account registered", USER: { full_name: "Siti Nurhaliza" }, created_at: new Date(Date.now() - 5 * 60000).toISOString() },
            { title: "Vaccination record updated", USER: { full_name: "Ahmad Abdullah" }, created_at: new Date(Date.now() - 23 * 60000).toISOString() },
            { title: "Appointment scheduled", USER: { full_name: "Nurul Izzah" }, created_at: new Date(Date.now() - 60 * 60000).toISOString() },
            { title: "Health record added", USER: { full_name: "Farah Liyana" }, created_at: new Date(Date.now() - 120 * 60000).toISOString() },
            { title: "Reminder sent for vaccination", USER: null, created_at: new Date(Date.now() - 180 * 60000).toISOString() },
        ];
        renderActivities(mockActivities, listContainer);
    } else {
        renderActivities(data, listContainer);
    }
}

function renderActivities(activities, container) {
    container.innerHTML = '';
    activities.forEach(act => {
        const timeAgo = getTimeAgo(act.created_at);
        const entityName = act.USER ? act.USER.full_name : 'System';
        const html = `
            <div class="list-item">
                <div>
                    <div class="list-item-title">${act.title || act.description || 'Activity'}</div>
                    <div class="list-item-subtitle">${entityName}</div>
                </div>
                <div class="list-item-time">${timeAgo}</div>
            </div>
        `;
        container.insertAdjacentHTML('beforeend', html);
    });
}

function getTimeAgo(dateString) {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 60) return `${diffMins} minutes ago`;
    if (diffHours < 24) return `${diffHours} hours ago`;
    return `${diffDays} days ago`;
}

function initCharts() {
    // Vaccination Chart
    const ctxVac = document.getElementById('vaccinationChart').getContext('2d');
    new Chart(ctxVac, {
        type: 'bar',
        data: {
            labels: ['Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr'],
            datasets: [{
                label: 'Completed',
                data: [0, 0, 0, 0, 0, 0, 0], // Mock empty bottom
                backgroundColor: '#10b981'
            }, {
                label: 'Pending',
                data: [140, 160, 190, 200, 220, 240, 260], // Mock data
                backgroundColor: '#ef4444'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { beginAtZero: true }
            }
        }
    });

    // Registration Chart
    const ctxReg = document.getElementById('registrationChart').getContext('2d');
    new Chart(ctxReg, {
        type: 'line',
        data: {
            labels: ['Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr'],
            datasets: [{
                label: 'Total Users',
                data: [1050, 1100, 1150, 1200, 1230, 1260, 1300],
                borderColor: '#3b82f6',
                tension: 0.1,
                fill: false
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}
