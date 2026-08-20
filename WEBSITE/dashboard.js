document.addEventListener('DOMContentLoaded', () => {
    loadDashboardData();
    initCharts();
});

async function loadDashboardData() {
    try {
        // Fetch Total Parents (assuming role isn't strictly enforced or we count all users)
        const { count: parentsCount, error: parentError } = await window.supabaseClient
            .from('USER')
            .select('*', { count: 'exact', head: true })
            .ilike('role', 'parent');
        
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

        const { count: overdueCount, error: overdueError } = await window.supabaseClient
            .from('CHILD_VACCINE')
            .select('*', { count: 'exact', head: true })
            .eq('status', 'Overdue');
        
        if (!overdueError) {
            document.getElementById('stat-overdue').innerText = overdueCount.toLocaleString();
        } else {
            document.getElementById('stat-overdue').innerText = '0';
        }

        // Fetch Recent Activities
        loadRecentActivities();

    } catch (err) {
        console.error("Error loading dashboard data:", err);
    }
}

async function loadRecentActivities() {
    const listContainer = document.getElementById('recent-activities-list');
    
    const { data, error } = await window.supabaseClient
        .from('ACTIVITY_LOG')
        .select('*, USER(full_name)')
        .order('created_at', { ascending: false })
        .limit(5);

    if (error) {
        console.error("Error fetching recent activities:", error);
        listContainer.innerHTML = '<div style="color: red; text-align: center; padding: 10px;">Failed to load activities</div>';
        return;
    }

    if (!data || data.length === 0) {
        listContainer.innerHTML = '<div style="text-align: center; padding: 10px; color: var(--text-muted);">No recent activities found</div>';
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
    if (!dateString) return 'Unknown time';
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now - date;
    
    if (diffMs < 0) return 'Just now'; // If time is slightly in the future due to clock sync

    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins === 0) return `Just now`;
    if (diffMins < 60) return `${diffMins} minute${diffMins > 1 ? 's' : ''} ago`;
    if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
    return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
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
                data: [120, 135, 150, 165, 180, 210, 225], 
                backgroundColor: '#10b981'
            }, {
                label: 'Pending',
                data: [20, 25, 40, 35, 40, 30, 35],
                backgroundColor: '#ef4444'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: { stacked: true },
                y: { beginAtZero: true, stacked: true }
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
