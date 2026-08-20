document.addEventListener('DOMContentLoaded', () => {
    let allRecords = [];
    let totalVaccines = 14; // Default/Mock total required

    loadVaccinationData();

    document.querySelectorAll('.filter-tab').forEach(tab => {
        tab.addEventListener('click', (e) => {
            document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
            e.target.classList.add('active');
            
            const filter = e.target.getAttribute('data-filter');
            renderVaccinations(allRecords, filter, document.getElementById('search-input').value);
        });
    });

    document.getElementById('search-input').addEventListener('input', (e) => {
        const activeTab = document.querySelector('.filter-tab.active').getAttribute('data-filter');
        renderVaccinations(allRecords, activeTab, e.target.value);
    });

    async function loadVaccinationData() {
        const container = document.getElementById('vaccination-records-container');
        try {
            // Get total required vaccines count
            const { count, error: vacError } = await window.supabaseClient
                .from('VACCINATION')
                .select('*', { count: 'exact', head: true });
            
            if (!vacError && count > 0) {
                totalVaccines = count;
            }

            // Fetch children with vaccination records
            const { data, error } = await window.supabaseClient
                .from('CHILD')
                .select(`
                    *,
                    USER!inner(full_name),
                    CHILD_VACCINE (status, administered_date, VACCINATION(vaccine_name, recommended_age_weeks))
                `)
                .order('full_name', { ascending: true });

            if (error) {
                console.error("Error fetching vaccination records:", error);
                container.innerHTML = `<div class="card" style="color: red;">Failed to load data</div>`;
                return;
            }

            // Process data to calculate statuses
            allRecords = data.map(child => {
                const vaccines = child.CHILD_VACCINE || [];
                const completed = vaccines.filter(v => (v.status || '').trim().toLowerCase() === 'completed').length;
                const pendingList = vaccines.filter(v => (v.status || '').trim().toLowerCase() === 'pending');
                const overdueList = vaccines.filter(v => (v.status || '').trim().toLowerCase() === 'overdue');
                
                const pending = pendingList.length;
                const overdue = overdueList.length;
                
                // Determine overall status
                let overallStatus = 'Pending';
                if (overdue > 0) {
                    overallStatus = 'Overdue';
                } else if (pending > 0) {
                    overallStatus = 'Pending';
                } else if (completed >= totalVaccines) {
                    overallStatus = 'Complete';
                } else if (completed > 0) {
                    overallStatus = 'Complete';
                }
                
                let nextDueStr = 'All vaccines completed';
                
                if (overallStatus === 'Overdue' && overdueList.length > 0) {
                    // Find the most overdue (earliest recommended age)
                    overdueList.sort((a, b) => (a.VACCINATION?.recommended_age_weeks || 0) - (b.VACCINATION?.recommended_age_weeks || 0));
                    const v = overdueList[0];
                    const vName = v.VACCINATION?.vaccine_name || 'Vaccine';
                    
                    if (child.date_of_birth && v.VACCINATION?.recommended_age_weeks !== undefined) {
                        const dueDate = new Date(child.date_of_birth);
                        dueDate.setDate(dueDate.getDate() + (v.VACCINATION.recommended_age_weeks * 7));
                        nextDueStr = `${vName} - Overdue since ${dueDate.toISOString().split('T')[0]}`;
                    } else {
                        nextDueStr = `${vName} - Overdue`;
                    }
                } else if (overallStatus === 'Pending' && pendingList.length > 0) {
                    // Find next pending
                    pendingList.sort((a, b) => (a.VACCINATION?.recommended_age_weeks || 0) - (b.VACCINATION?.recommended_age_weeks || 0));
                    const v = pendingList[0];
                    const vName = v.VACCINATION?.vaccine_name || 'Vaccine';
                    
                    if (child.date_of_birth && v.VACCINATION?.recommended_age_weeks !== undefined) {
                        const dueDate = new Date(child.date_of_birth);
                        dueDate.setDate(dueDate.getDate() + (v.VACCINATION.recommended_age_weeks * 7));
                        nextDueStr = `${vName} - Due: ${dueDate.toISOString().split('T')[0]}`;
                    } else {
                        nextDueStr = `${vName} - Pending`;
                    }
                } else if (overallStatus === 'Pending') {
                     nextDueStr = 'Waiting for records';
                }

                return {
                    ...child,
                    vacStats: { completed, pending, overdue, total: totalVaccines },
                    overallStatus,
                    nextDueStr
                };
            });

            updateSummaryCards();
            renderVaccinations(allRecords, 'all', '');

        } catch (err) {
            console.error("Unexpected error:", err);
        }
    }

    function calculateAge(dob) {
        if (!dob) return 'N/A';
        const birthDate = new Date(dob);
        const today = new Date();
        let age = today.getFullYear() - birthDate.getFullYear();
        const m = today.getMonth() - birthDate.getMonth();
        if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
            age--;
        }
        return age > 0 ? `${age} years` : 'Under 1 year';
    }

    function updateSummaryCards() {
        const total = allRecords.length;
        const upToDate = allRecords.filter(r => r.overallStatus === 'Complete').length;
        const pending = allRecords.filter(r => r.overallStatus === 'Pending').length;
        const overdue = allRecords.filter(r => r.overallStatus === 'Overdue').length;

        document.getElementById('vac-total').innerText = total;
        document.getElementById('vac-uptodate').innerText = upToDate;
        document.getElementById('vac-pending').innerText = pending;
        document.getElementById('vac-overdue').innerText = overdue;
    }

    function renderVaccinations(data, filter, search) {
        const container = document.getElementById('vaccination-records-container');
        
        let filteredData = data;
        
        if (filter !== 'all') {
            filteredData = filteredData.filter(r => r.overallStatus === filter);
        }

        if (search) {
            const query = search.toLowerCase();
            filteredData = filteredData.filter(r => {
                const childName = (r.full_name || '').toLowerCase();
                const parentName = (r.USER?.full_name || '').toLowerCase();
                return childName.includes(query) || parentName.includes(query);
            });
        }

        if (filteredData.length === 0) {
            container.innerHTML = `<div class="card" style="text-align: center;">No records found.</div>`;
            return;
        }

        container.innerHTML = '';
        filteredData.forEach(record => {
            const parentName = record.USER ? record.USER.full_name : 'Unknown Parent';
            const age = calculateAge(record.date_of_birth);
            
            let cardStyle = '';
            let iconHtml = '';
            let btnStyle = 'background-color: white;';
            
            if (record.overallStatus === 'Complete') {
                cardStyle = 'background-color: var(--success-bg); border-color: var(--success-light);';
                iconHtml = `<div style="display: flex; gap: 8px; align-items: center; background: white; padding: 6px 12px; border-radius: 20px; font-size: 13px; font-weight: 500; color: var(--success-color);"><i class="ph ph-check-circle" style="font-size: 16px;"></i> Complete</div>`;
                btnStyle = 'background-color: white; color: var(--success-color); border-color: transparent;';
            } else if (record.overallStatus === 'Pending') {
                cardStyle = 'background-color: var(--primary-light); border-color: #dbeafe;';
                iconHtml = `<div style="display: flex; gap: 8px; align-items: center; background: white; padding: 6px 12px; border-radius: 20px; font-size: 13px; font-weight: 500; color: var(--primary-color);"><i class="ph ph-clock" style="font-size: 16px;"></i> Pending</div>`;
                btnStyle = 'background-color: white; color: var(--primary-color); border-color: transparent;';
            } else if (record.overallStatus === 'Overdue') {
                cardStyle = 'background-color: var(--danger-bg); border-color: var(--danger-light);';
                iconHtml = `<div style="display: flex; gap: 8px; align-items: center; background: white; padding: 6px 12px; border-radius: 20px; font-size: 13px; font-weight: 500; color: var(--danger-color);"><i class="ph ph-warning-circle" style="font-size: 16px;"></i> Overdue</div>`;
                btnStyle = 'background-color: white; color: var(--danger-color); border-color: transparent;';
            }

            const html = `
                <div class="card" style="${cardStyle}">
                    <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px;">
                        <div>
                            <h3 style="font-size: 18px; margin-bottom: 4px; color: var(--text-main);">${record.full_name}</h3>
                            <p style="color: var(--text-muted); font-size: 13px;">Parent: ${parentName} • Age: ${age}</p>
                        </div>
                        ${iconHtml}
                    </div>

                    <div class="grid-4" style="gap: 16px; margin-bottom: 16px;">
                        <div style="background-color: white; padding: 16px; border-radius: 8px;">
                            <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Total Required</span>
                            <span style="font-size: 18px; color: var(--text-main);">${record.vacStats.total}</span>
                        </div>
                        <div style="background-color: white; padding: 16px; border-radius: 8px;">
                            <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Completed</span>
                            <span style="font-size: 18px; color: var(--success-color);">${record.vacStats.completed}</span>
                        </div>
                        <div style="background-color: white; padding: 16px; border-radius: 8px;">
                            <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Pending</span>
                            <span style="font-size: 18px; color: var(--primary-color);">${record.vacStats.pending}</span>
                        </div>
                        <div style="background-color: white; padding: 16px; border-radius: 8px;">
                            <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Overdue</span>
                            <span style="font-size: 18px; color: var(--danger-color);">${record.vacStats.overdue}</span>
                        </div>
                    </div>

                    <div style="background-color: white; padding: 16px; border-radius: 8px; margin-bottom: 16px;">
                        <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Next Due</span>
                        <span style="font-size: 14px; color: var(--text-main);">${record.nextDueStr}</span>
                    </div>

                    <div style="display: flex; gap: 12px;">
                        <button class="btn btn-outline" style="${btnStyle}">Send Reminder</button>
                        <button class="btn btn-outline" style="${btnStyle}">Update Status</button>
                    </div>
                </div>
            `;
            container.insertAdjacentHTML('beforeend', html);
        });
    }
});
