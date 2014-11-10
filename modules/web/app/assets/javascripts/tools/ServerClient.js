define(['jquery'], function (jquery) {

    var endpoint = "ws://localhost:9000/socket";


    var reconnectInterval = 3000;
    var connectionEstablishTimeout = 5000;

    var currentState = WebSocket.CLOSED;

    var attemptsSoFar = 0;
    var forcedClose = false;

    var listeners = [];

    var sock;


    var key2alias = {};
    var alias2key = {};
    var aliasCounter = 1;



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
            console.debug("Websocket open at " + endpoint + " after " + attemptsSoFar + " attempts");
            attemptsSoFar = 0;
            connection = sock;

            listeners.forEach(function (next) {
                if (next.onOpen) next.onOpen();
            });
        };

        sock.onclose = function (x) {
            clearTimeout(timeout);
            sock = null;
            alias2key = {};
            key2alias = {};

            if (currentState == WebSocket.OPEN) {
                listeners.forEach(function (next) {
                    if (next.onClosed) next.onClosed();
                });
            }

            currentState = WebSocket.CLOSED;

            if (!forcedClose) {
                setTimeout(function () {
                    attemptConnection();
                }, reconnectInterval);
            }
        };

        sock.onmessage = function (e) {
            console.debug("From Websocket: " + e.data);

            var type = e.data.substring(0,1);

            var aliasAndData = e.data.substring(1).split('|');

            var path = alias2key[aliasAndData[0]];
            var payload = aliasAndData[1] ? JSON.parse(aliasAndData[1]) : false;

            console.debug("Alias conversion: " + aliasAndData[0] + " -> " + path);

            var segments = path.split('|');
            var route = segments[0];
            var topic = segments[1];

            console.debug("From Websocket: " + type + " : " + route + " : " + topic + " : " + payload);

            var eventId = route + "|" + topic;

            listeners.forEach(function (next) {
                next.onMessage(eventId, type, payload);
            });

        };

    }

    function connected() {
        return currentState == WebSocket.OPEN;
    }


    function sendToServer(type, route, topic, payload) {
        if (connected()) {
            var key = route + "|" + topic;
            if (!key2alias[key]) {
                aliasCounter++;

                var alias = aliasCounter.toString(32);

                key2alias[key] = aliasCounter;
                alias2key[alias] = key;
                sock.send('A' + alias + "|" + key);
                key = alias;
            } else {
                key = key2alias[key];
            }
            sock.send(type + key + "|" + (payload ? JSON.stringify(payload) : ""));
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
                onMessage: function (eventId, type, payload) {
                    if (messageHandlers[eventId]) messageHandlers[eventId](type, payload);
                },
                stop: stopFunc,
                subscribe: subscribeFunc,
                unsubscribe: unsubscribeFunc,
                command: function (route, topic, data) {
                    return sendToServer("C", route, topic, data);
                },
                connected: connected,
                addWsOpenEventListener: function (callback) {
                    this.onOpen = callback;
                },
                addWsClosedEventListener: function (callback) {
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
                    messageHandlers[route + "|" + topic] = callback;
                }
            }

            function unsubscribeFunc(route, topic, callback) {
                if (sendToServer("U", route, topic, false)) {
                    console.log("Unregistered interest: {" + route + "}" + topic);
                    messageHandlers[route + "|" + topic] = null;
                }
            }

            listeners.push(handle);

            return handle;

        }
    };


});
