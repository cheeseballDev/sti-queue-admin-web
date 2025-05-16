// ADD A FUCKING CLASS TO MAKE IT LOOK LIKE AN ACTUAL FILE FFS
let callNextQueueId;
let queueElement = document.getElementById(queueId);
let contentDiv = queueElement.closest('.content');
let laneTitleElement = contentDiv.querySelector('.lane-title');
    
function callNext(queueId) {
    callNextQueueId = queueId;

    let counterText = laneTitleElement.innerText.trim();
    let counterNumber = parseInt(counterText.split(' ')[1]);

    let activeQueueType = document.querySelector('.tabs span.active');
    let queueType = activeQueueType.textContent.trim().toLowerCase();
    
    incrementQueue(queueType, counterNumber);
    return;
}

//GET
async function updateUI() {
    // add a GET function that gets the counter number button
}

async function incrementQueue(queueType, counterNumber) {
    let apiUrl = `/api/${queueType}/next?queueType=${encodeURIComponent(queueType)}&counterNumber=${encodeURIComponent(counterNumber)}`;

    const response = await fetch(apiUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    });

    if (response.ok) {
        const data = await response.json();
        console.log(data);
        const queueElement = document.getElementById(callNextQueueId);
        if (queueElement && data.nextQueueNumber !== undefined) {
            queueElement.innerText = data.nextQueueNumber;
        } else if (data && data.warning) {
            // display warning message to the admin
            console.warn("Queue warning:", data.warning);
        } else if (response.status === 204) {
            // Handle the No Content response (no increment)
            console.warn("Queue is already at the current number.");
            // document.getElementById('queueWarning').innerText = "Queue is at the latest number.";
        }
    }
}

function togglePause(button) {
    // add the isOnBreak function here
    if (button.innerText.includes('Pause')) {
        button.innerText = 'Resume ▶';
        
        let activeQueueType = document.querySelector('.tabs span.active');
        let queueType = activeQueueType.textContent.trim().toLowerCase();

        let counterText = laneTitleElement.innerText.trim();
        let counterNumber = parseInt(counterText.split(' ')[1]);
        setOnBreak(queueType, counterNumber);
        return;
    }
    button.innerText = 'Pause ⏸';
}

async function setOnPause(queueType, counterNumber) {
    let apiUrl = `/api/${queueType}/pause?queueType=${encodeURIComponent(queueType)}&counterNumber=${encodeURIComponent(counterNumber)}`;

    const response = await fetch(apiUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    });

    if (response.ok) {
        const data = await response.json();
        console.log(data);
    }
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


function resetQueues() {
    const confirmReset = confirm("Do you really want to reset the Queue?");
    if (confirmReset) {
        document.getElementById("queueNumber1").innerText = "0";
        document.getElementById("queueNumber2").innerText = "0";
        document.getElementById("queueNumber3").innerText = "0";
    }
}

