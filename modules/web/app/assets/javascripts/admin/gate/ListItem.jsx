/** @jsx React.DOM */
define(['react', 'subscriberMixin'], function (React, subscriberMixin) {

    return React.createClass({
        mixins: [subscriberMixin],

        subscriptionId: function () {
            return "/gates/list/" + this.props.id;
        },
        getInitialState: function () {
            return {data: null, another: 1}
        },

        renderData: function() {
            return (
                <div>
                    {this.state.data.username} : {this.state.data.text} : {this.state.another}
                </div>
            );
        },
        renderLoading: function() {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.data) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});