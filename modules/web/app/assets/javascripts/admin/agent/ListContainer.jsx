/** @jsx React.DOM */
define(['react', 'admin/agent/List'],
    function (React, List) {

    return React.createClass({

        getInitialState: function () {
            return {}
        },

        render: function () {
            return (
                <div>
                    <List addr={this.props.addr} />
                </div>
            )
        }
    });

});