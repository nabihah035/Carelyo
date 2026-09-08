document.addEventListener('DOMContentLoaded', () => {
    const sessionData = localStorage.getItem('carelyo_admin_session');
    if (!sessionData) {
        window.location.href = 'login.html';
        return;
    }
    const userSession = JSON.parse(sessionData);
    const clinicId = userSession.clinicid;

    loadDashboardData(clinicId);
});

async function loadDashboardData(clinicId) {
    try {
        if (clinicId) {
            const { data: clinicData, error: clinicError } = await window.supabaseClient
                .from('CLINIC')
                .select('clinic_name')
                .eq('clinicid', clinicId)
                .single();
                
            if (!clinicError && clinicData) {
                document.getElementById('clinic-name-display').innerText = clinicData.clinic_name;
            } else {
                document.getElementById('clinic-name-display').innerText = 'Unknown Clinic';
            }
        } else {
            document.getElementById('clinic-name-display').innerText = 'All Clinics';
        }

        // Fetch Total Parents (assuming role isn't strictly enforced or we count all users)
        let parentQuery = window.supabaseClient
            .from('USER')
            .select('*', { count: 'exact', head: true })
            .ilike('role', 'parent');
            
        if (clinicId) parentQuery = parentQuery.eq('clinicid', clinicId);

        const { count: parentsCount, error: parentError } = await parentQuery;
        
        if (!parentError) {
            document.getElementById('stat-parents').innerText = parentsCount.toLocaleString();
        }

        // Fetch Children Registered
        let childQuery = window.supabaseClient
            .from('CHILD')
            .select('childid, USER!inner(clinicid)', { count: 'exact', head: true });
            
        if (clinicId) childQuery = childQuery.eq('USER.clinicid', clinicId);

        const { count: childrenCount, error: childError } = await childQuery;
        
        if (!childError) {
            document.getElementById('stat-children').innerText = childrenCount.toLocaleString();
        }

        // Fetch Appointments Today
        const today = new Date().toISOString().split('T')[0];
        let apptQuery = window.supabaseClient
            .from('APPOINTMENT')
            .select('*', { count: 'exact', head: true })
            .eq('appointment_date', today);
            
        if (clinicId) apptQuery = apptQuery.eq('clinicid', clinicId);

        const { count: apptCount, error: apptError } = await apptQuery;
        
        if (!apptError) {
            document.getElementById('stat-appointments').innerText = apptCount.toLocaleString();
        }

        let overdueQuery = window.supabaseClient
            .from('CHILD_VACCINE')
            .select('childvaccineid, CHILD!inner(USER!inner(clinicid))')
            .eq('status', 'Overdue');
            
        if (clinicId) overdueQuery = overdueQuery.eq('CHILD.USER.clinicid', clinicId);

        const { data: overdueData, error: overdueError } = await overdueQuery;
        
        if (!overdueError && overdueData) {
            document.getElementById('stat-overdue').innerText = overdueData.length.toLocaleString();
        } else {
            document.getElementById('stat-overdue').innerText = '0';
        }

    } catch (err) {
        console.error("Error loading dashboard data:", err);
    }
}
