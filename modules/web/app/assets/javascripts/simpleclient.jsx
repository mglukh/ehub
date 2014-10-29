/** @jsx React.DOM */
define(['react'], function(React) {

    var sock = new WebSocket("ws://localhost:9000/socket");
    var connection = null;
    sock.onmessage = function (e) {
        console.log("Received!: " + e.data)
        var data = e.data
        listeners.forEach(function (next) {
            next.onMessage(JSON.parse(data));
        })
    }

    var listeners = [];

    function _addListener(ref) {
        listeners.push(ref)
        if (connection != null) {
            ref.onConnected();
        } else {
            ref.onDisconnected();
        }
    }

    function _removeListener(ref) {
        listeners = listeners.filter(function (next) {
            return next != ref;
        })
    }

    sock.onopen = function (x) {
        console.log("Open " + x)
        sock.send(JSON.stringify({type: "source", system: "BX"}));
        connection = sock;
        listeners.forEach(function (next) {
            next.onConnected();
        })
    }

    return {
        addListener: _addListener,
        removeListener: _removeListener,
        connected: function() { return connection != null; },
        send: function(x) { connection.send(x); }

    };

});