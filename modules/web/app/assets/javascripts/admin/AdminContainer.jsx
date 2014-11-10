/** @jsx React.DOM */
define(['react','admin/gate/ListContainer','admin/agent/ListContainer'],
    function (React, GatesContainer, AgentsContainer) {

    return React.createClass({

        getInitialState: function () {
            return {}
        },

        render: function () {
            return (
                <div>
                    <GatesContainer />
                    <hr/>
                    <AgentsContainer/>
                </div>
            )
        }
    });

});