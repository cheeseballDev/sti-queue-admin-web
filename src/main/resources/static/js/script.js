const counterElementIds = {
    'counter1Serving': 'queueNumber1',
    'counter2Serving': 'queueNumber2',
    'counter3Serving': 'queueNumber3',
};

// Get a reference to the SINGLE tickets list element
const ticketsListElement = document.getElementById("ticketsList");
if (!ticketsListElement) {
    console.error("Error: Element with ID 'ticketsList' not found in HTML. Please ensure it's added.");
} else {
    ticketsListElement.innerHTML = '<li>Loading all active tickets...</li>'; // Initial state for global list
}

/*
    BUTTON FUNCTIONS (These functions interact with the specific counters on the current page)
*/

function getQueueInfo(queueId) {
    const queueElement = document.getElementById(queueId);
    if (!queueElement) { /* ... error handling ... */ return null; }

    const contentDiv = queueElement.closest('.content');
    if (!contentDiv) { /* ... error handling ... */ return null; }

    const laneTitleElement = contentDiv.querySelector('.lane-title');
    if (!laneTitleElement) { /* ... error handling ... */ return null; }

    const counterText = laneTitleElement.innerText.trim();
    const counterNumber = parseInt(counterText.split(' ')[1]);
    if (isNaN(counterNumber)) { /* ... error handling ... */ return null; }

    const activeQueueTypeElement = document.querySelector('.tabs span.active');
    const queueType = activeQueueTypeElement ? activeQueueTypeElement.textContent.trim().toUpperCase() : '';

    if (!queueType) { /* ... error handling ... */ return null; }

    return { queueElement, counterNumber, queueType };
}

// ... (callNext, togglePause, resetQueues functions remain the same) ...
function callNext(queueId) {
    const info = getQueueInfo(queueId);
    if (info) {
        incrementQueue(info.queueType, info.counterNumber, queueId);
    }
}

function togglePause(button, queueId) {
    const isPausing = button.innerText.includes('Pause');
    const newButtonText = isPausing ? 'Resume ▶' : 'Pause ⏸';
    button.innerText = newButtonText;

    const info = getQueueInfo(queueId);
    if (info) {
        setOnBreak(info.queueType, info.counterNumber, isPausing);
    }
}

function resetQueues() {
    const activeQueueTypeElement = document.querySelector('.tabs span.active');
    const queueType = activeQueueTypeElement.textContent.trim().toLowerCase();
    swal({
        title: "Do you really want to reset the queue?",
        text: "This will reset the queue for " + queueType,
        icon: "warning",
        dangerMode: true,
        buttons: { cancel: "No", confirm: { text: "Yes", value: true } }
    })
    .then((value) => {
        if (value) {
            if (activeQueueTypeElement) {
                clearTickets(queueType);
            }
            // Reset visible counter numbers to 0 immediately (WebSocket will confirm)
            document.getElementById("queueNumber1").innerText = "0";
            document.getElementById("queueNumber2").innerText = "0";
            document.getElementById("queueNumber3").innerText = "0";
            // Also reset the global ticket list display
            if (ticketsListElement) {
                ticketsListElement.innerHTML = '<li>No tickets across all queues.</li>';
            }
        }
    });
}


/*
    ASYNC FUNCTIONS (REST API calls)
*/
async function incrementQueue(queueType, counterNumber, queueId) {
    const apiUrl = `/api/${queueType.toUpperCase()}/next?queueType=${encodeURIComponent(queueType.toUpperCase())}&counterNumber=${encodeURIComponent(counterNumber)}`;
    try {
        const response = await fetch(apiUrl, { method: 'POST', headers: { 'Content-Type': 'application/json' } });
        if (response.status === 204) { showNotification("Queue is already at the current number.", "info"); return; }
        if (!response.ok) { showNotification(`Failed to increment queue: ${response.status} - ${response.statusText}`, "error"); }
        console.log("Increment Queue Response:", await response.json());
    } catch (error) {
        showNotification(`A server error has occurred while incrementing! ${error.message}`, "error");
        console.error("Increment Queue Error:", error);
    }
}

async function setOnBreak(queueType, counterNumber, isPausing) {
    const action = isPausing ? 'pause' : 'resume';
    const apiUrl = `/api/${queueType.toUpperCase()}/togglepause?queueType=${encodeURIComponent(queueType.toUpperCase())}&counterNumber=${encodeURIComponent(counterNumber)}`;
    try {
        const response = await fetch(apiUrl, { method: 'POST', headers: { 'Content-Type': 'application/json' } });
        if (!response.ok) { showNotification(`Failed to ${action} queue: ${response.status} - ${response.statusText}`, "error"); }
        console.log("Toggle Pause Response:", await response.json());
    } catch (error) {
        showNotification(`Error during toggling pause: ${error.message}`, "error");
        console.error("Toggle Pause Error:", error);
    }
}

async function clearTickets(queueType) {
    const apiUrl = `/api/${queueType}/clear?queueType=${encodeURIComponent(queueType)}`;
    try {
        const response = await fetch(apiUrl, { method: 'DELETE', headers: { 'Content-Type': 'application/json' } });
        if (response.ok) { swal("Success!", "Cleared all tickets for this queue!", "success"); }
        else { showNotification(`Failed to clear queue: ${response.status} - ${response.statusText}`, "error"); }
    } catch (error) {
        showNotification(`Error during clearing tickets: ${error.message}`, "error");
        console.error("Clear Tickets Error:", error);
    }
}


/*
    WEBSOCKET (Main Logic)
*/
const websocket = new WebSocket('ws://localhost:8080/ws-updates');

websocket.onopen = (event) => {
    console.log("WebSocket connection opened:", event);
};

websocket.onmessage = (event) => {
    try {
        const data = JSON.parse(event.data);
        console.log("Received WebSocket message:", data);

        const activeQueueTypeElement = document.querySelector('.tabs span.active');
        const currentPageServiceType = activeQueueTypeElement ? activeQueueTypeElement.textContent.trim().toUpperCase() : '';

        if (data.type === "COUNTER_UPDATE") {
            // Only update counters if the message is for *this specific page's service*.
            // (e.g., admissions.html only updates if data.serviceType is 'ADMISSION')
            if (data.serviceType === currentPageServiceType) {
                document.getElementById(counterElementIds.counter1Serving).innerText = (data.counter1Serving !== undefined && data.counter1Serving !== null) ? data.counter1Serving : 0;
                document.getElementById(counterElementIds.counter2Serving).innerText = (data.counter2Serving !== undefined && data.counter2Serving !== null) ? data.counter2Serving : 0;
                document.getElementById(counterElementIds.counter3Serving).innerText = (data.counter3Serving !== undefined && data.counter3Serving !== null) ? data.counter3Serving : 0;
            }
        } else if (data.type === "TICKET_UPDATE") {
            // This section updates the SINGLE, GLOBAL ticket list
            // It does NOT check data.serviceType because the list is persistent across all services
            if (ticketsListElement) {
                ticketsListElement.innerHTML = ''; // Clear previous list

                if (data.tickets && data.tickets.length > 0) {
                    // Sort tickets (e.g., by service type, then by number) for readability
                    data.tickets.sort((a, b) => {
                        // Prioritize PWD tickets, then sort by service, then by number
                        if (a.isPWD && !b.isPWD) return -1;
                        if (!a.isPWD && b.isPWD) return 1;

                        if (a.service && b.service && a.service !== b.service) {
                            return a.service.localeCompare(b.service);
                        }
                        return (a.number || 0) - (b.number || 0);
                    });

                    data.tickets.forEach(ticket => {
                        const li = document.createElement('li');
                        // Display the service type for each ticket since it's a global list
                        let ticketDisplay = `[${ticket.service || 'UNKNOWN'}] Ticket #${ticket.number}`;
                        if (ticket.status) { ticketDisplay += ` (Status: ${ticket.status})`; }
                        if (ticket.isPWD) { ticketDisplay += ` (PWD)`; }
                        li.innerText = ticketDisplay;
                        ticketsListElement.appendChild(li);
                    });
                } else {
                    const li = document.createElement('li');
                    li.innerText = 'No waiting/serving tickets across all queues.';
                    ticketsListElement.appendChild(li);
                }
            }
        } else {
            console.warn("Received unknown message type:", data.type, data);
        }

    } catch (error) {
        console.error("Error parsing or processing WebSocket message:", error, event.data);
    }
};

websocket.onclose = (event) => {
    console.warn("WebSocket connection closed:", event.code, event.reason);
    setTimeout(() => {
        console.log("Attempting to reconnect WebSocket...");
        new WebSocket('ws://localhost:8080/ws-updates');
    }, 5000);
};

websocket.onerror = (error) => {
    console.error("WebSocket error:", error);
    showNotification("WebSocket connection error. Please check your network or server.", "error");
};

/*
    MISC FUNCTIONS
*/

function showNotification(message, iconType) {
    swal({ title: "INFORMATION", text: message, icon: iconType });
}

function printForm() {
    let printWindow = window.open('', '', 'height=500,width=800');
    printWindow.document.write('<html><head><title>Form</title></head><body>');
    printWindow.document.write('<h1>STI Queue Form</h1>');
    printWindow.document.write('<p>This is a sample form for queue printing.</p>');
    printWindow.document.write('</body></html>');
    printWindow.document.close();
    printWindow.print();
}

// Ensure all functions are globally accessible via the window object
// This is necessary because your HTML uses inline onclick attributes (e.g., onclick="callNext('queueNumber1')")
window.callNext = callNext;
window.togglePause = togglePause;
window.resetQueues = resetQueues;
window.printForm = printForm;
window.showNotification = showNotification;
// window.confirmReset is from your modal, ensure it's defined elsewhere if not here.
// You removed the modal confirmation logic from resetQueues to use sweetalert,
// so you might not need a separate confirmReset function anymore unless your modal is still active.