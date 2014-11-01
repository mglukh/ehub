/** @jsx React.DOM */
define(['react', 'subscriberMixin'], function (React, subscriberMixin) {

    return React.createClass({
        mixins: [subscriberMixin],
        subscriptionId: function () {
            return "/gates/list/" + this.props.id;
        },
        getInitialState: function () {
            return {data: {}, another: 1}
        },
        onDataUpdate: function (data) {
            this.setState({data: data})
        },


        renderLineWithData: function() {
            return (
                <div>
                    {this.state.data.username} : {this.state.data.text} : {this.state.another}
                </div>
            );
        },
        renderEmptyLine: function() {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.data) {
                return this.renderLineWithData();
            } else {
                return this.renderEmptyLine();
            }
        }
    });

});