document.addEventListener('DOMContentLoaded', () => {
    loadAppointments();
    loadParentsForDropdown();

    document.querySelectorAll('.filter-tab').forEach(tab => {
        tab.addEventListener('click', (e) => {
            // Update active state
            document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
            e.currentTarget.classList.add('active');

            // Filter data
            const filter = e.currentTarget.getAttribute('data-filter');
            renderAppointments(allAppointments, filter);
        });
    });

    document.getElementById('appointment-form').addEventListener('submit', saveAppointment);
});

let allAppointments = [];
let parentsData = []; // Store parents and their children

async function loadAppointments() {
    const container = document.getElementById('appointments-container');
    try {
        const { data, error } = await window.supabaseClient
            .from('APPOINTMENT')
            .select(`
                *,
                USER (full_name),
                CHILD (full_name)
            `)
            .order('appointment_date', { ascending: false })
            .order('appointment_time', { ascending: false });

        if (error) {
            console.error("Error fetching appointments:", error);
            container.innerHTML = `<div class="card" style="color: red;">Failed to load data</div>`;
            return;
        }

        allAppointments = data || [];
        updateCounts();
        const activeFilter = document.querySelector('.filter-tab.active').getAttribute('data-filter');
        renderAppointments(allAppointments, activeFilter);

    } catch (err) {
        console.error("Unexpected error:", err);
    }
}

async function loadParentsForDropdown() {
    try {
        // Fetch parents and their children
        const { data, error } = await window.supabaseClient
            .from('USER')
            .select(`
                userid, full_name,
                CHILD (childid, full_name)
            `)
            .ilike('role', 'parent')
            .order('full_name', { ascending: true });
            
        if (error) throw error;
        parentsData = data || [];
        
        const select = document.getElementById('appt-parent');
        select.innerHTML = '<option value="">Select a parent...</option>';
        parentsData.forEach(p => {
            select.innerHTML += `<option value="${p.userid}">${p.full_name}</option>`;
        });
    } catch (err) {
        console.error("Error fetching parents for dropdown:", err);
    }
}

function loadChildrenForDropdown(selectedChildId = null) {
    const parentId = document.getElementById('appt-parent').value;
    const childSelect = document.getElementById('appt-child');
    
    if (!parentId) {
        childSelect.innerHTML = '<option value="">Select a parent first...</option>';
        childSelect.disabled = true;
        return;
    }

    const parent = parentsData.find(p => p.userid == parentId);
    if (!parent || !parent.CHILD || parent.CHILD.length === 0) {
        childSelect.innerHTML = '<option value="">No children found</option>';
        childSelect.disabled = true;
        return;
    }

    childSelect.innerHTML = '<option value="">Select a child...</option>';
    parent.CHILD.forEach(c => {
        childSelect.innerHTML += `<option value="${c.childid}">${c.full_name}</option>`;
    });
    
    childSelect.disabled = false;
    
    if (selectedChildId) {
        childSelect.value = selectedChildId;
    }
}

function updateCounts() {
    const total = allAppointments.length;
    const scheduled = allAppointments.filter(a => (a.status || 'Scheduled').trim().toLowerCase() === 'scheduled').length;
    const upcoming = allAppointments.filter(a => (a.status || '').trim().toLowerCase() === 'upcoming').length;
    const completed = allAppointments.filter(a => (a.status || '').trim().toLowerCase() === 'completed').length;
    const cancelled = allAppointments.filter(a => (a.status || '').trim().toLowerCase() === 'cancelled').length;

    document.getElementById('filter-all').innerText = `All (${total})`;
    document.getElementById('filter-scheduled').innerText = `Scheduled (${scheduled})`;
    document.getElementById('filter-upcoming').innerText = `Upcoming (${upcoming})`;
    document.getElementById('filter-completed').innerText = `Completed (${completed})`;
    document.getElementById('filter-cancelled').innerText = `Cancelled (${cancelled})`;
}

function renderAppointments(data, filter) {
    const container = document.getElementById('appointments-container');
    
    let filteredData = data;
    if (filter && filter.toLowerCase() !== 'all') {
        filteredData = data.filter(a => {
            const status = (a.status || 'Scheduled').trim().toLowerCase();
            return status === filter.toLowerCase();
        });
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
        
        let timeStr = appt.appointment_time || 'N/A';
        if (timeStr !== 'N/A') {
            const parts = timeStr.split(':');
            if (parts.length >= 2) {
                let hour = parseInt(parts[0], 10);
                const ampm = hour >= 12 ? 'PM' : 'AM';
                hour = hour % 12;
                hour = hour ? hour : 12;
                timeStr = `${hour.toString().padStart(2, '0')}:${parts[1]} ${ampm}`;
            }
        }

        const statusValue = (appt.status || 'Scheduled').trim().toLowerCase();
        let statusClass = 'badge-primary'; // default fallback
        if (statusValue === 'completed') statusClass = 'badge-success';
        if (statusValue === 'cancelled') statusClass = 'badge-danger';
        if (statusValue === 'upcoming') statusClass = 'badge-warning';
        if (statusValue === 'scheduled') statusClass = 'badge-purple';
        
        const statusText = appt.status || 'Scheduled';

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
                    <button class="btn btn-outline" onclick="openModal(${appt.appid})">Edit</button>
                    ${statusText !== 'Cancelled' ? `<button class="btn btn-outline" style="color: var(--danger-color); border-color: var(--danger-light);" onclick="cancelAppointment(${appt.appid})">Cancel</button>` : ''}
                </div>
            </div>
        `;
        container.insertAdjacentHTML('beforeend', html);
    });
}

async function openModal(id = null) {
    const modal = document.getElementById('appointment-modal');
    const form = document.getElementById('appointment-form');
    const title = document.getElementById('modal-title');
    
    form.reset();
    document.getElementById('appt-id').value = '';
    document.getElementById('appt-child').innerHTML = '<option value="">Select a parent first...</option>';
    document.getElementById('appt-child').disabled = true;

    if (id) {
        title.innerText = 'Edit Appointment';

        try {
            const { data, error } = await window.supabaseClient
                .from('APPOINTMENT')
                .select('*')
                .eq('appid', id)
                .single();
            
            if (error) throw error;
            if (data) {
                document.getElementById('appt-id').value = data.appid;
                document.getElementById('appt-parent').value = data.parentid || '';
                loadChildrenForDropdown(data.childid);
                document.getElementById('appt-date').value = data.appointment_date || '';
                document.getElementById('appt-time').value = data.appointment_time || '';
                document.getElementById('appt-clinic').value = data.clinic_name || '';
                document.getElementById('appt-doctor').value = data.doctor_name || '';
                document.getElementById('appt-purpose').value = data.purpose || '';
                document.getElementById('appt-status').value = data.status || 'Scheduled';
                document.getElementById('appt-notes').value = data.notes || '';
            }
        } catch (err) {
            console.error('Error fetching appointment details:', err);
            alert('Could not fetch details.');
            return;
        }
    } else {
        title.innerText = 'New Appointment';
        document.getElementById('appt-status').value = 'Scheduled';
    }

    modal.style.display = 'flex';
}

function closeModal() {
    document.getElementById('appointment-modal').style.display = 'none';
}

async function saveAppointment(e) {
    e.preventDefault();
    const btn = document.getElementById('save-appt-btn');
    btn.disabled = true;
    btn.innerText = 'Saving...';

    const id = document.getElementById('appt-id').value;
    const parentId = document.getElementById('appt-parent').value;
    const childId = document.getElementById('appt-child').value;
    
    const payload = {
        parentid: parentId,
        childid: childId,
        appointment_date: document.getElementById('appt-date').value,
        appointment_time: document.getElementById('appt-time').value,
        clinic_name: document.getElementById('appt-clinic').value,
        doctor_name: document.getElementById('appt-doctor').value,
        purpose: document.getElementById('appt-purpose').value,
        status: document.getElementById('appt-status').value,
        notes: document.getElementById('appt-notes').value
    };

    try {
        if (id) {
            const { error } = await window.supabaseClient
                .from('APPOINTMENT')
                .update(payload)
                .eq('appid', id);
            
            if (error) throw error;
            alert('Appointment updated successfully!');
        } else {
            const { error } = await window.supabaseClient
                .from('APPOINTMENT')
                .insert([payload]);
            
            if (error) throw error;
            alert('Appointment added successfully!');
        }
        
        closeModal();
        await loadAppointments();
    } catch (err) {
        console.error('Error saving appointment:', err);
        alert(err.message || 'Error saving appointment.');
    } finally {
        btn.disabled = false;
        btn.innerText = 'Save';
    }
}

async function cancelAppointment(id) {
    if (!confirm('Are you sure you want to cancel this appointment?')) {
        return;
    }

    try {
        const { error } = await window.supabaseClient
            .from('APPOINTMENT')
            .update({ status: 'Cancelled' })
            .eq('appid', id);
        
        if (error) throw error;
        alert('Appointment cancelled successfully!');
        await loadAppointments();
    } catch (err) {
        console.error('Error cancelling appointment:', err);
        alert(err.message || 'Error cancelling appointment.');
    }
}
