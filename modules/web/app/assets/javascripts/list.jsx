/** @jsx React.DOM */
define(['react', 'AutoSubscribeMixin'], function(React, AutoSubscribeMixin) {


    var List = React.createClass({
        mixins: [AutoSubscribeMixin],
        subscriptionId: function() {
            return "/gates/list";
        },
        getInitialState: function () {
            return {list: [], another: 1}
        },
        onDataUpdate: function(data) {
            this.setState({list: data.data})
        },
        componentDidMount: function() {
            this.startSubscription();
        },
        componentWillUnmount: function() {
            this.stopSubscription();
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