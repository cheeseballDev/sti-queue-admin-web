let queueInfo

function getQueueInfo(queueId) {
    const queueElement = document.getElementById(queueId);
    
    const contentDiv = queueElement.closest('.content');
    const laneTitleElement = contentDiv.querySelector('.lane-title');

    const counterText = laneTitleElement.innerText.trim();
    const counterNumber = parseInt(counterText.split(' ')[1]);
    const activeQueueType = document.querySelector('.tabs span.active');

    const queueType = activeQueueType.textContent.trim().toLowerCase();

    return { queueElement, counterNumber, queueType };
}

function callNext(queueId) {
    const queueInfo = getQueueInfo(queueId);
    if (!queueInfo) return;
    incrementQueue(queueInfo.queueType, queueInfo.counterNumber, queueId);
}

async function incrementQueue(queueType, counterNumber, queueId) {
    const apiUrl = `/api/${queueType}/next?queueType=${encodeURIComponent(queueType)}&counterNumber=${encodeURIComponent(counterNumber)}`;

    try {
        const response = await fetch(apiUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.status == 204) {
            showNotification("Queue is already at the current number.", "info");
            return;
        }

        if (response.ok) {
            const data = await response.json();
            console.log(data);
            const queueElement = document.getElementById(queueId);
            if (queueElement && data.nextQueueNumber !== undefined) {
                queueElement.innerText = data.nextQueueNumber;
            }
        } 
    } catch (error) {
        showNotification(`A server error has occured while incrementing!`, "error")
    }
}

function togglePause(button, queueId) {
    const isPausing = button.innerText.includes('Pause');
    const newButtonText = isPausing ? 'Resume ▶' : 'Pause ⏸';
    button.innerText = newButtonText;

    const queueInfo = getQueueInfo(queueId);
    if (queueInfo) {
        setOnBreak(queueInfo.queueType, queueInfo.counterNumber, isPausing);
    }
    setOnBreak(queueInfo.queueType, queueInfo.counterNumber, isPausing);
}

async function setOnBreak(queueType, counterNumber, isPausing) {
    const action = isPausing ? 'pause' : 'resume';
    const apiUrl = `/api/${queueType}/togglepause?queueType=${encodeURIComponent(queueType)}&counterNumber=${encodeURIComponent(counterNumber)}`;

    try {
        const response = await fetch(apiUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            const data = await response.json();
            console.log(data);
        } else {
            showNotification(`Failed to ${action} queue: ${response.status}`, "error")
        }   
    } catch (error) {
        showNotification(`Error during toggling pause:`, "error")
    }
}


function connectWebSocket(queueType) {
    const websocketUrl = `ws://localhost:8080/ws/queue/${queueType}`; // Replace with your actual backend URL and port
    const websocket = new WebSocket(websocketUrl);

    websocket.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            if (data.currentServing !== undefined) {
                console.log(`Real-time update for ${queueType}: Current Serving - ${data.currentServing}`);
                const servingDisplay = document.getElementById(`${queueType}-current-serving`);
                if (servingDisplay) {
                    servingDisplay.innerText = data.currentServing;
                }
            }
        } catch (error) {
            console.error("Error parsing WebSocket message:", error);
        }
    };

    websocket.onclose = () => {
        console.log(`WebSocket connection closed for ${queueType}`);
        setTimeout(() => {
            connectWebSocket(queueType);
        }, 3000);
    };

    websocket.onerror = (error) => {
        console.error(`WebSocket error for ${queueType}:`, error);
    };
}

connectWebSocket('admission');
connectWebSocket('cashier');
connectWebSocket('registrar');


function printForm() {
    let printWindow = window.open('', '', 'height=500,width=800');
    printWindow.document.write('<html><head><title>Form</title></head><body>');
    printWindow.document.write('<h1>STI Queue Form</h1>');
    printWindow.document.write('<p>This is a sample form for queue printing.</p>');
    printWindow.document.write('</body></html>');
    printWindow.document.close();
    printWindow.print();
}

function resetQueues() {
    swal({
        title: "WARNING:",
        text: "Do you really want to reset the queue?",
        icon: "warning",
        dangerMode: true,
        buttons: {
            cancel: "No",
            confirm: {
                text: "Yes",
                value: true
            }
        }
    })
    .then((value) => {
        if (value) {
            const activeQueueTypeElement = document.querySelector('.tabs span.active');
            if (activeQueueTypeElement) {
                const queueType = activeQueueTypeElement.textContent.trim().toLowerCase();
                for (let i = 1; i <= 3; i++) {
                    clearTickets(queueType, i);
                }
            }
            document.getElementById("queueNumber1").innerText = "0";
            document.getElementById("queueNumber2").innerText = "0";
            document.getElementById("queueNumber3").innerText = "0";
        }
    });
}

async function clearTickets(queueType, counterNumber) {
    const apiUrl = `/api/${queueType}/clear?queueType=${encodeURIComponent(queueType)}&counterNumber=${encodeURIComponent(counterNumber)}`;

    try {
        const response = await fetch(apiUrl, {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            swal("Success!", "Cleared all tickets for this queue!", "success");
        } else {
            showNotification(`Failed to ${action} queue: ${response.status}`, "error")
        }   
    } catch (error) {
        showNotification(`Error during ${action}:`, "error")
    }
}

function showNotification(message, iconType) {
    swal({
        title: "INFORMATION",
        text: message,
        icon: iconType,
    })
}

