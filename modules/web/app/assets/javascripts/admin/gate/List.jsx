/** @jsx React.DOM */
define(['react', 'subscriberMixin', 'admin/gate/ListItem'], function(React, subscriberMixin, ListItem) {

    return React.createClass({
        mixins: [subscriberMixin],
        subscriptionId: function() {
            return "/gates/list";
        },
        getInitialState: function () {
            return {list: [], another: 1}
        },
        onDataUpdate: function(data) {
            this.setState({list: data})
        },
        render: function () {
            return (
                <div>
                    <h4>List:</h4>
                {this.state.list.length}
                {
                    this.state.list.map(function (el) {
                        return <ListItem id={el.id}/>;
                    })
                    }

                </div>
            );
        }
    });

});