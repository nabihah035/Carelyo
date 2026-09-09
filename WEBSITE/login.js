document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('login-form');
    const loginBtn = document.getElementById('login-btn');
    const errorMsg = document.getElementById('error-msg');

    // Check if already logged in
    const session = localStorage.getItem('carelyo_admin_session');
    if (session) {
        window.location.href = 'index.html';
    }

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const email = document.getElementById('email').value;
        const password = document.getElementById('password').value;
        
        loginBtn.disabled = true;
        loginBtn.innerText = 'Logging in...';
        errorMsg.style.display = 'none';

        // Query the USER table directly
        const { data: userData, error: userError } = await window.supabaseClient
            .from('USER')
            .select('*')
            .eq('email', email)
            .eq('password', password)
            .single();

        if (userError || !userData) {
            errorMsg.innerText = 'Invalid credentials. Please try again.';
            errorMsg.style.display = 'block';
            loginBtn.disabled = false;
            loginBtn.innerText = 'Log In';
            return;
        }

        const userRole = userData.role ? userData.role.toLowerCase() : '';

        if (userRole === 'staff') {
            // Fetch clinicid from CLINIC_STAFF and details from CLINIC
            try {
                const { data: staffData, error: staffErr } = await window.supabaseClient
                    .from('CLINIC_STAFF')
                    .select('*')
                    .eq('userid', userData.userid)
                    .maybeSingle();

                if (staffData && staffData.clinicid) {
                    userData.clinicid = staffData.clinicid;
                    userData.role_at_clinic = staffData.role_at_clinic;

                    // Fetch clinic details from CLINIC table
                    const { data: clinicData, error: clinicErr } = await window.supabaseClient
                        .from('CLINIC')
                        .select('*')
                        .eq('clinicid', staffData.clinicid)
                        .maybeSingle();

                    if (clinicData) {
                        userData.clinic = clinicData;
                        userData.clinic_name = clinicData.clinic_name;
                    }
                }
            } catch (err) {
                console.error("Error retrieving clinic details:", err);
            }

            // Successfully logged in and authorized
            localStorage.setItem('carelyo_admin_session', JSON.stringify(userData));
            window.location.href = 'index.html';
        } else {
            // Unauthorized role
            errorMsg.innerText = 'Access denied. Only staff are allowed. Parents are not authorized.';
            errorMsg.style.display = 'block';
            loginBtn.disabled = false;
            loginBtn.innerText = 'Log In';
        }
    });
});
