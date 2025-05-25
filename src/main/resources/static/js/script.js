const counterElementIds = {
    'counter1Serving': 'queueNumber1',
    'counter2Serving': 'queueNumber2',
    'counter3Serving': 'queueNumber3',
};

let ticketsList;
let currentPageServiceType;

/*
    HELPER FUNCTIONS 
*/

function formatTicketDate(createdAt) {
    if (createdAt && typeof createdAt === 'object' && createdAt.seconds !== undefined) {
        const date = new Date(createdAt.seconds * 1000);
        return date.toLocaleString();
    }
    if (createdAt) {
        const date = new Date(createdAt);
        if (!isNaN(date.getTime())) {
            return date.toLocaleString();
        }
    }
    return 'Unknown Date'; // Fallback for invalid or missing date
}

/*
    TICKET FORMATTER
*/

// Template for a single ticket list item (now using the helper function)
const ticketTemplate = (ticket) => `
    <li class="ticket-item ${ticket.status ? ticket.status.toLowerCase() : ''} ${ticket.isPWD ? 'pwd' : ''}">
        <strong>#${ticket.number}</strong> - ${ticket.service || 'UNKNOWN'} -
        ${formatTicketDate(ticket.createdAt)}
        <br>Ticket ID: ${ticket.id}
        ${ticket.isPWD ? ' (PWD)' : ''}
        ${ticket.status ? ` (Status: ${ticket.status})` : ''}
    </li>
`;

// Main rendering function for the tickets list
const renderTickets = (serviceType, tickets) => {
    // Ensure the container is available. This check should already be done in DOMContentLoaded.
    if (!ticketsList) {
        console.error("renderTickets: ticketsList is null. Cannot render.");
        return;
    }

    ticketsList.innerHTML = ''; // Clear the list once here

    // Update the heading for the current service type
    const parent = ticketsList.parentElement;
    let heading = parent.querySelector('h3'); // Assuming h3 is "All Active Tickets"
    if (!heading) {
        heading = document.createElement('h3');
        parent.insertBefore(heading, ticketsList);
    }
    // You might want to update the heading to reflect the *filtered* tickets
    // or keep it generic as "All Active Tickets" if the backend sends all.
    // Given your backend sends tickets for the specific serviceType, this heading is fine.
    heading.textContent = `Active Tickets for ${serviceType.toUpperCase()}`;


    if (!tickets || tickets.length === 0) {
        const li = document.createElement('li');
        li.innerText = 'No waiting/serving tickets for this service.';
        ticketsList.appendChild(li);
        return;
    }

    // Sort tickets (your existing sorting logic)
    tickets.sort((a, b) => {
        if (a.isPWD && !b.isPWD) return -1;
        if (!a.isPWD && b.isPWD) return 1;
        if (a.service && b.service && a.service !== b.service) {
            return a.service.localeCompare(b.service);
        }
        return (a.number || 0) - (b.number || 0);
    });

    // Append each ticket using the template
    tickets.forEach(ticket => {
        const li = document.createElement('li'); // Create a new LI for each ticket
        li.innerHTML = ticketTemplate(ticket); // Populate with the template HTML
        ticketsList.appendChild(li); // Append to the UL
    });
};


/*
    BUTTON FUNCTIONS (These functions interact with the specific counters on the current page)
*/

function getQueueInfo(queueId) {
    const queueElement = document.getElementById(queueId);

    const contentDiv = queueElement.closest('.content');

    const laneTitleElement = contentDiv.querySelector('.lane-title');
    
    const counterText = laneTitleElement.innerText.trim();
    const counterNumber = parseInt(counterText.split(' ')[1]);
    
    const activeQueueTypeElement = document.querySelector('.tabs span.active');
    const queueType = activeQueueTypeElement ? activeQueueTypeElement.textContent.trim().toUpperCase() : '';

    const checkbox = contentDiv.querySelector('input[type="checkbox"]'); 
    const isChecked = checkbox.checked;

    return { queueElement, counterNumber, queueType: currentPageServiceType, isChecked };
}

function callNext(queueId) {
    const info = getQueueInfo(queueId);
    if (info) {
        incrementQueue(info.queueType, info.counterNumber, info.isChecked);
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
            // to be removed 
            document.getElementById("queueNumber1").innerText = "0";
            document.getElementById("queueNumber2").innerText = "0";
            document.getElementById("queueNumber3").innerText = "0";
            if (ticketsList) {
                ticketsList.innerHTML = '<li>No tickets across all queues.</li>';
            }
        }
    });
}

/*
    ASYNC FUNCTIONS (REST API calls)
*/
async function incrementQueue(queueType, counterNumber, isChecked) {
    const apiUrl = `/api/${queueType.toUpperCase()}/next?queueType=${encodeURIComponent(queueType.toUpperCase())}&counterNumber=${encodeURIComponent(counterNumber)}&isChecked=${encodeURI(isChecked)}`;
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

document.addEventListener('DOMContentLoaded', () => {
    ticketsList = document.getElementById("ticketsList");
    ticketsList.innerHTML = '<li>Loading all active tickets...</li>';

    const activeQueueTypeElement = document.querySelector('.tabs span.active');
    currentPageServiceType = activeQueueTypeElement ? activeQueueTypeElement.textContent.trim().toUpperCase() : '';

    const websocket = new WebSocket('ws://localhost:8080/ws-updates');

    websocket.onopen = (event) => {
        console.log("WebSocket connection opened:", event);

        websocket.send(JSON.stringify({type: "CURRENT_SERVICE", serviceType: currentPageServiceType}));
    };

    websocket.onmessage = (event) => {
    try {
        const data = JSON.parse(event.data);
        console.log("Received WebSocket message:", data);
        
        if (data.type === "COUNTER_UPDATE") {
            if (data.serviceType === currentPageServiceType) {
                document.getElementById(counterElementIds.counter1Serving).innerText = (data.counter1Serving !== undefined && data.counter1Serving !== null) ? data.counter1Serving : 0;
                document.getElementById(counterElementIds.counter2Serving).innerText = (data.counter2Serving !== undefined && data.counter2Serving !== null) ? data.counter2Serving : 0;
                document.getElementById(counterElementIds.counter3Serving).innerText = (data.counter3Serving !== undefined && data.counter3Serving !== null) ? data.counter3Serving : 0;
            }
        } 
        if (data.type === "TICKET_UPDATE") {
            if (ticketsList) {
                ticketsList.innerHTML = ''; 
                if (data.tickets && data.tickets.length > 0) {
                    data.tickets.sort((a, b) => {
                        if (a.isPWD && !b.isPWD) return -1;
                        if (!a.isPWD && b.isPWD) return 1;

                        if (a.service && b.service && a.service !== b.service) {
                            return a.service.localeCompare(b.service);
                        }
                        return (a.number || 0) - (b.number || 0);
                    });
                    renderTickets(data.serviceType, data.tickets);
                    return;    
                }
                const li = document.createElement('li');
                li.innerText = 'No waiting/serving tickets across all queues.';
                ticketsList.appendChild(li);
            }
        }
        } catch (error) {
            console.error("Error parsing or processing WebSocket message:", error, event.data);
        }
    }
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
    });

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

window.callNext = callNext;
window.togglePause = togglePause;
window.resetQueues = resetQueues;
window.printForm = printForm;
window.showNotification = showNotification;