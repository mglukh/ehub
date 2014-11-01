define(['jquery'], function(jquery) {

    var sock = new WebSocket("ws://localhost:9000/socket");
    var connection = null;
    sock.onmessage = function (e) {
        console.log("Received!: " + e.data);
        var data = e.data;
        listeners.forEach(function (next) {
            if (next.interestedIn(e.data.topic)) {
                next.ref.onMessage(JSON.parse(data));
            }
        });
    };

    var listeners = [];

    sock.onopen = function (x) {
        console.log("Open " + x);
        sock.send(JSON.stringify({type: "source", system: "BX"}));
        connection = sock;
        listeners.forEach(function (next) {
            next.ref.onConnected();
        });
    };

    return {
        addListener: function (ref) {

            var seed = Math.random().toString(36).substr(2, 10);

            var interest = [];
            var struc = {
                ref: ref,
                seed: seed,
                interestedIn: function(val) {
                    return $.inArray(val, interest);
                }
            };

            listeners.push(struc);

            if (connection !== null) {
                ref.onConnected();
            } else {
                ref.onDisconnected();
            }


            return {

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

        }


    };

});