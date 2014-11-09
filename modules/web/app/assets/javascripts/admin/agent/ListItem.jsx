/** @jsx React.DOM */
define([
        'react',
        'subscriberMixin',
        'admin/gate/StartStopButton'
    ],
    function (React,
              subscriberMixin,
              StartStopButton) {

        return React.createClass({
            mixins: [subscriberMixin],

            subscriptionConfig: function () {
                return {route: this.props.id, topic: 'info', target: 'info'};
            },
            getInitialState: function () {
                return {info: null}
            },

            renderData: function () {
                return (
                    <div>
                    {this.state.info.name} {this.state.info_stale ? "Stale" : "not stale"} : {this.state.info.text} :
                        <StartStopButton state={this.state.info.state} route={this.props.id} />
                    </div>
                );
            },
            renderLoading: function () {
                return (
                    <div>loading...</div>
                );
            },

            render: function () {
                if (this.state.info) {
                    return this.renderData();
                } else {
                    return this.renderLoading();
                }
            }
        });

    });