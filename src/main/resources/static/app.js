document.addEventListener('DOMContentLoaded', () => {
    const contentArea = document.getElementById('content-area');
    const navLinks = document.querySelectorAll('.nav-link');

    // --- Page-specific logic ---
    const pageScripts = {
        'dashboard': () => {
            const statusEl = document.getElementById('status-indicator');
            const logContainer = document.getElementById('log-container');
            let socket;

            function logMessage(message) {
                const now = new Date();
                const timestamp = now.toLocaleTimeString();
                const logEntry = document.createElement('div');
                try {
                    const parsed = JSON.parse(message);
                    logEntry.textContent = `[${timestamp}] ${parsed.type}: ${parsed.name}`;
                } catch (e) {
                    logEntry.textContent = `[${timestamp}] ${message}`;
                }
                logContainer.prepend(logEntry);
            }

            function connect() {
                socket = new WebSocket(`ws://${window.location.host}/events/live`);
                socket.onopen = () => { statusEl.textContent = 'Connected'; statusEl.className = 'connected'; };
                socket.onclose = () => { statusEl.textContent = 'Offline'; statusEl.className = 'disconnected'; setTimeout(connect, 5000); };
                socket.onmessage = (event) => logMessage(event.data);
            }
            connect();
        },
        'users': () => {
            const userTableBody = document.querySelector("#user-table tbody");
            fetch('/users')
                .then(response => response.json())
                .then(users => {
                    userTableBody.innerHTML = '';
                    if (users.length === 0) {
                        userTableBody.innerHTML = '<tr><td colspan="3">No users found in the database.</td></tr>';
                        return;
                    }
                    users.forEach(user => {
                        const row = document.createElement('tr');
                        row.innerHTML = `<td>${user.id}</td><td>${user.name}</td><td>${user.role}</td>`;
                        userTableBody.appendChild(row);
                    });
                })
                .catch(error => {
                    console.error('Error fetching users:', error);
                    userTableBody.innerHTML = '<tr><td colspan="3">Failed to load user data.</td></tr>';
                });
        }
    };

    // --- Router logic ---
    async function loadContent(path) {
        try {
            const response = await fetch(path + '.html');
            if (!response.ok) throw new Error('Page not found');
            contentArea.innerHTML = await response.text();

            // Run the script for the loaded page
            const scriptName = path.split('/').pop();
            if (pageScripts[scriptName]) {
                pageScripts[scriptName]();
            }
        } catch (error) {
            contentArea.innerHTML = `<h2>Error</h2><p>Could not load page. Please try again.</p>`;
            console.error(error);
        }
    }

    function handleNavigation() {
        const hash = window.location.hash.substring(1) || 'dashboard';
        loadContent(hash);

        navLinks.forEach(link => {
            if (link.getAttribute('href') === '#' + hash) {
                link.classList.add('active');
            } else {
                link.classList.remove('active');
            }
        });
    }

    window.addEventListener('hashchange', handleNavigation);

    // Load initial content
    handleNavigation();
});