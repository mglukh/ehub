define(['jquery'], function (jquery) {

    var endpoint = "ws://localhost:9000/socket";


    var reconnectInterval = 3000;
    var connectionEstablishTimeout = 5000;

    var currentState = WebSocket.CLOSED;

    var attemptsSoFar = 0;
    var forcedClose = false;

    var listeners = [];

    var sock;


    function attemptConnection() {
        attemptsSoFar++;
        currentState = WebSocket.CONNECTING;

        sock = new WebSocket(endpoint);

        console.debug("Attempting to connect to " + endpoint);

        var timeout = setTimeout(function () {
            if (sock) sock.close();
        }, connectionEstablishTimeout);

        sock.onopen = function (x) {
            currentState = WebSocket.OPEN;
            clearTimeout(timeout);
            console.debug("Websocket open at " + endpoint+" after " + attemptsSoFar +" attempts");
            attemptsSoFar = 0;
            connection = sock;

            listeners.forEach(function (next) {
                if (next.onOpen) next.onOpen();
            });
        };

        sock.onclose = function (x) {
            clearTimeout(timeout);
            sock = null;

            if (currentState == WebSocket.OPEN) {
                listeners.forEach(function (next) {
                    if (next.onClosed) next.onClosed();
                });
            }

            currentState = WebSocket.CLOSED;

            if (!forcedClose) {
                setTimeout(function() {
                    attemptConnection();
                }, reconnectInterval);
            }
        };

        sock.onmessage = function (e) {
            console.debug("From Websocket: " + e.data);

            var segments = e.data.split('|');
            var type = segments[0];
            var route = segments[1];
            var topic = segments[2];
            var payload = segments[3] ? JSON.parse(segments[3]) : false;

            console.debug("From Websocket: " + type + " : " + route + " : " + topic + " : " + payload);

            var eventId = type+"|"+route+"|"+topic;

            listeners.forEach(function (next) {
                next.onMessage(eventId, payload);
            });


            //if (type == 'U') {
            //    listeners.forEach(function (next) {
            //        if (next.interestedIn(route, topic)) {
            //            console.log("!>>> U -> " + route + "|" + topic);
            //            next.ref.onMessage(payload);
            //        }
            //    });
            //} else if (type == 'D') {
            //    listeners.forEach(function (next) {
            //        if (next.interestedIn(route, topic)) {
            //            console.log("!>>> D -> " + route + "|" + topic);
            //            next.ref.onDiscard(payload);
            //            next.removeInterest(route, topic);
            //        }
            //    });
            //}

        };

    }

    function connected() {
        return currentState == WebSocket.OPEN;
    }


    function sendToServer(type, route, topic, payload) {
        if (connected()) {
            sock.send(type + "|" + route + "|" + topic + "|" + (payload ? JSON.stringify(payload) : ""));
            return true;
        } else {
            return false;
        }
    }

    attemptConnection();

    return {
        getHandle: function () {

            var messageHandlers = {};

            var handle = {
                //uid: Math.random().toString(36).substr(2, 10),
                onMessage: function (eventId, payload) {
                    if (messageHandlers[eventId]) messageHandlers[eventId](payload);
                },
                stop: stopFunc,
                subscribe: subscribeFunc,
                unsubscribe: unsubscribeFunc,
                command: function (route, topic, data) {
                    return sendToServer("C", route, topic, data);
                },
                connected: connected,
                addWsOpenEventListener: function(callback) {
                    this.onOpen = callback;
                },
                addWsClosedEventListener: function(callback) {
                    this.onClosed = callback;
                }
            };

            function stopFunc() {
                listeners = listeners.filter(function (next) {
                    return next != handle;
                });
            }

            function subscribeFunc(route, topic, callback) {
                if (sendToServer("S", route, topic, false)) {
                    console.log("Registered interest: {" + route + "}" + topic);
                    messageHandlers["U|"+route+"|"+topic] = callback;
                }
            }

            function unsubscribeFunc(route, topic, callback) {
                if (sendToServer("U", route, topic, false)) {
                    console.log("Unregistered interest: {" + route + "}" + topic);
                    messageHandlers["U|"+route+"|"+topic] = null;
                }
            }

            listeners.push(handle);

            return handle;

        }
    };


});
