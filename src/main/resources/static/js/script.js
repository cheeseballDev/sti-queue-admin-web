// ADD A FUCKING CLASS TO MAKE IT LOOK LIKE AN ACTUAL FILE FFS

let callNextQueueId;

function callNext(queueId) {
    let queueElement = document.getElementById(queueId);
    let contentDiv = queueElement.closest('.content');
    let laneTitleElement = contentDiv.querySelector('.lane-title');
    let counterText = laneTitleElement.innerText.trim();
    let counterNumber = parseInt(counterText.split(' ')[1]);
    callNextQueueId = queueId;

    console.log("Counter Number:", counterNumber);

    incrementQueue(counterNumber);
    return;
}

//GET
async function updateUI() {
    // add post function that gets the counter number button
}

async function incrementQueue(counterNumber) {
    try {
        const activeSpan = document.querySelector('.tabs span.active');
        if (!activeSpan) return;

        const activeTab = activeSpan.textContent.trim().toLowerCase();
        let apiUrl = `/api/${activeTab}/next?counterNumber=${encodeURIComponent(counterNumber)}`;

        const response = await fetch(apiUrl, {
            method: 'GET',
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
                // Optionally display a message
                // document.getElementById('queueWarning').innerText = "Queue is at the latest number.";
            }
        } else {
            console.error('Error:', response.status);
            // Handle other error scenarios
        }

    } catch (error) {
        console.error('Error:', error);
    }
}

function togglePause(button) {
    // add the isOnBreak function here
    if (button.innerText.includes('Pause')) {
        button.innerText = 'Resume ▶';
        return;
    }
    button.innerText = 'Pause ⏸';
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

