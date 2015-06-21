var ws = new WebSocket('ws://ec2-54-157-230-140.compute-1.amazonaws.com/topic/events');

function disable_form() {
    document.getElementById('form').style.opacity = 0.5;
}

function enable_form() {
    document.getElementById('form').style.opacity = 1;
}

ws.onerror = function(e) {
    console.log(e)
    disable_form();
}

ws.onmessage = function(m) {
    var elem = document.getElementById('messages');
    var msg = document.createElement('li');
    msg.setAttribute('class', 'message');
    msg.appendChild(document.createTextNode(m.data));
    if(elem.childNodes.length == 4) {
        elem.removeChild(elem.lastChild);
    }
    if(elem.firstChild) {
        elem.insertBefore(msg, elem.firstChild);
    } else {
        elem.appendChild(msg);
    }
}

ws.onopen = function(e) {
    enable_form();
}

function send() {
    var elem = document.getElementById('text');
    var msg = elem.value;
    if(0 < msg.length) {
        ws.send(msg);
    }
    elem.value = '';
    elem.focus();
}
