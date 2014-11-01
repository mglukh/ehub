/** @jsx React.DOM */
define(['react', 'simpleclient'], function(React, client) {


    var List = React.createClass({
        getInitialState: function () {
            return {list: [], another: 1}
        },
        componentDidMount: function () {

            var _this = this;

            this.listener = {
                onConnected: function () {
                    _this.sess.subscribe("/gates/list");
                },
                onDisconnected: function () {
                },
                onMessage: function (data) {
                    _this.setState({list: data.data})
                }
            };
            this.sess = client.addListener(this.listener)
        },
        componentWillUnmount: function () {
            this.sess.stop()
        },
        render: function () {
            return (
                <div>
                    <h4>List:</h4>
                {this.state.list.length}
                {
                    this.state.list.map(function (el) {
                        return <div>{el.username} : {el.text} : this.state.another</div>;
                    })
                    }

                </div>
            );
        }
    });

    return List;

});