function callNext(queueId) {
  let queueElement = document.getElementById(queueId);
  let queueNumber = parseInt(queueElement.innerText);
  incrementQueue();
}

async function incrementQueue() {
    try {
        const activeSpan = document.querySelector('.tabs span.active');
        if (!activeSpan) return;

        const activeTab = activeSpan.textContent.trim().toLowerCase();
        let apiUrl = `/api/${activeTab}/next`;

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
                // Optionally, clear any previous warning message
                // document.getElementById('queueWarning').innerText = '';
            } else if (data && data.warning) {
                // Display the warning message to the admin
                console.warn("Queue warning:", data.warning);
                // You could update a specific element on your page to show this warning
                // document.getElementById('queueWarning').innerText = data.warning;
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
