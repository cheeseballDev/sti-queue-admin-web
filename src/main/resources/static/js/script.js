function callNext(queueId) {
  let queueElement = document.getElementById(queueId);
  let queueNumber = parseInt(queueElement.innerText);
  incrementQueue();
  if (queueNumber > 0) {
    queueElement.innerText = queueNumber - 1;
  }
}

async function incrementQueue() {
  try {
    const activeSpan = document.querySelector('.tabs span.active');
    if (!activeSpan) return;

    const activeTab = activeSpan.textContent.trim();
    
    const response = await fetch(`/api/?tab=${encodeURIComponent(activeTab)}`);
    const data = await response.json();
    console.log(data);
  } catch (error) {
    console.error('Error:', error);
  }
}

function togglePause(button) {
  if (button.innerText.includes('Pause')) {
    button.innerText = 'Resume ▶';
  } else {
    button.innerText = 'Pause ⏸';
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
