define(['jquery'], function(jquery) {

    var endpoint = "ws://localhost:9000/socket";

    var sock = new WebSocket(endpoint);
    var connection = null;
    var listeners = [];
    sock.onmessage = function (e) {
        console.debug("From Websocket: " + e.data);
        var data = JSON.parse(e.data);
        listeners.forEach(function (next) {
            if (next.interestedIn(data.topic)) {
                next.ref.onMessage(data.payload);
            }
        });
    };

    sock.onopen = function (x) {
        console.debug("Websocket open at " + endpoint);
        connection = sock;
        listeners.forEach(function (next) {
            next.ref.onConnected(next.handle);
        });
    };



    return {
        addListener: function (ref) {

            var seed = Math.random().toString(36).substr(2, 10);

            var interest = [];

            var handle = {

                stop: function() {
                    listeners = listeners.filter(function (next) {
                        return next.seed != seed;
                    });
                    console.log("Stopped: " +seed );
                },
                subscribe: function(topic) {
                    interest.push(topic);
                    connection.send(JSON.stringify({type: "sub", topic: topic}));
                    console.log("Registered interest: " + topic + " seed: " +seed );
                },
                unsubscribe: function(topic) {
                    interest = interest.filter(function(next) {
                        return next != topic;
                    });
                    connection.send(JSON.stringify({type: "unsub", topic: topic}));
                    console.log("Unregistered interest: " + topic + " seed: " +seed );
                },
                command: function(topic, data) {
                    connection.send(JSON.stringify({type: "cmd", topic: topic, data: data}));
                    console.log("Sent command into: " + topic + " seed: " +seed );
                }
            };

            var struc = {
                ref: ref,
                seed: seed,
                interestedIn: function(val) {
                    return $.inArray(val, interest) > -1;
                },
                handle: handle
            };



            listeners.push(struc);

            if (connection !== null) {
                ref.onConnected(handle);
            } else {
                ref.onDisconnected(handle);
            }


            return handle;

        }
    };


});
