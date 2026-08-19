document.addEventListener('DOMContentLoaded', () => {
    let allAppointments = [];

    loadAppointments();

    document.querySelectorAll('.filter-tab').forEach(tab => {
        tab.addEventListener('click', (e) => {
            // Update active state
            document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
            e.target.classList.add('active');

            // Filter data
            const filter = e.target.getAttribute('data-filter');
            renderAppointments(allAppointments, filter);
        });
    });

    async function loadAppointments() {
        const container = document.getElementById('appointments-container');
        try {
            // Fetch appointments with related parent and child info
            const { data, error } = await window.supabaseClient
                .from('APPOINTMENT')
                .select(`
                    *,
                    USER (full_name),
                    CHILD (full_name)
                `)
                .order('appointment_date', { ascending: true });

            if (error) {
                console.error("Error fetching appointments:", error);
                container.innerHTML = `<div class="card" style="color: red;">Failed to load data</div>`;
                return;
            }

            allAppointments = data || [];
            updateCounts();
            renderAppointments(allAppointments, 'all');

        } catch (err) {
            console.error("Unexpected error:", err);
        }
    }

    function updateCounts() {
        const total = allAppointments.length;
        const confirmed = allAppointments.filter(a => a.status === 'Confirmed').length;
        const pending = allAppointments.filter(a => a.status === 'Pending').length;

        document.getElementById('filter-all').innerText = `All (${total})`;
        document.getElementById('filter-confirmed').innerText = `Confirmed (${confirmed})`;
        document.getElementById('filter-pending').innerText = `Pending (${pending})`;
    }

    function renderAppointments(data, filter) {
        const container = document.getElementById('appointments-container');
        
        let filteredData = data;
        if (filter !== 'all') {
            filteredData = data.filter(a => a.status === filter);
        }

        if (filteredData.length === 0) {
            container.innerHTML = `<div class="card" style="text-align: center;">No appointments found.</div>`;
            return;
        }

        container.innerHTML = '';
        filteredData.forEach(appt => {
            const childName = appt.CHILD ? appt.CHILD.full_name : 'Unknown Child';
            const parentName = appt.USER ? appt.USER.full_name : 'Unknown Parent';
            const dateStr = formatDate(appt.appointment_date);
            
            // Format time (assuming time without time zone from db)
            let timeStr = appt.appointment_time || 'N/A';
            if (timeStr !== 'N/A') {
                // simple format if it comes as "HH:MM:SS"
                const parts = timeStr.split(':');
                if (parts.length >= 2) {
                    let hour = parseInt(parts[0], 10);
                    const ampm = hour >= 12 ? 'PM' : 'AM';
                    hour = hour % 12;
                    hour = hour ? hour : 12; // 0 should be 12
                    timeStr = `${hour.toString().padStart(2, '0')}:${parts[1]} ${ampm}`;
                }
            }

            const statusClass = appt.status === 'Confirmed' ? 'badge-success' : (appt.status === 'Pending' ? 'badge-warning' : 'badge-danger');
            const statusText = appt.status || 'Pending'; // Default if null

            const html = `
                <div class="card" style="position: relative;">
                    <div style="position: absolute; right: 24px; top: 24px;">
                        <span class="badge ${statusClass}">${statusText}</span>
                    </div>
                    
                    <h3 style="margin-bottom: 4px; font-size: 18px;">${childName}</h3>
                    <p style="color: var(--text-muted); font-size: 14px; margin-bottom: 16px;">Parent: ${parentName}</p>
                    
                    <div class="grid-2" style="margin-bottom: 16px;">
                        <div style="display: flex; gap: 8px; align-items: center; color: var(--text-muted); font-size: 14px;">
                            <i class="ph ph-calendar-blank" style="font-size: 18px;"></i>
                            ${dateStr}
                        </div>
                        <div style="display: flex; gap: 8px; align-items: center; color: var(--text-muted); font-size: 14px;">
                            <i class="ph ph-clock" style="font-size: 18px;"></i>
                            ${timeStr}
                        </div>
                        <div style="display: flex; gap: 8px; align-items: center; color: var(--text-muted); font-size: 14px;">
                            <i class="ph ph-map-pin" style="font-size: 18px;"></i>
                            ${appt.clinic_name || 'N/A'}
                        </div>
                        <div style="display: flex; gap: 8px; align-items: center; color: var(--text-muted); font-size: 14px;">
                            <i class="ph ph-user" style="font-size: 18px;"></i>
                            ${appt.doctor_name || 'N/A'}
                        </div>
                    </div>

                    <div style="background-color: var(--bg-main); padding: 16px; border-radius: 8px; margin-bottom: 16px;">
                        <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Reason for Visit</span>
                        <span style="font-size: 14px;">${appt.purpose || 'Not specified'}</span>
                    </div>

                    <div style="display: flex; gap: 12px;">
                        <button class="btn btn-primary">View Details</button>
                        <button class="btn btn-outline">Edit</button>
                        <button class="btn btn-outline" style="color: var(--danger-color); border-color: var(--danger-light);">Cancel</button>
                    </div>
                </div>
            `;
            container.insertAdjacentHTML('beforeend', html);
        });
    }
});
