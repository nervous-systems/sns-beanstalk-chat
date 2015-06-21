var ws = new WebSocket('ws://sns-beanstalk-chat.elasticbeanstalk.com/topic/events');

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
    setInterval(
        function() {
            console.log('Heartbeat!');
            ws.send(JSON.stringify(["heartbeat"]));
        }, 30 * 1000);
    enable_form();
}

function disable_form() {
    document.getElementById('form').style.opacity = 0.5;
}

function enable_form() {
    document.getElementById('form').style.opacity = 1;
}

function send() {
    var elem = document.getElementById('text');
    var msg = elem.value;
    if(0 < msg.length) {
        ws.send(JSON.stringify(["message", msg]));
    }
    elem.value = '';
    elem.focus();
}
