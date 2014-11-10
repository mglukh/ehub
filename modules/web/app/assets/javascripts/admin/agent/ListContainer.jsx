/** @jsx React.DOM */
define(['react', 'admin/agent/List', 'admin/agent/datatap/ListContainer'],
    function (React, List, TapListContainer) {

    return React.createClass({

        getInitialState: function () {
            return { selection: false }
        },

        handleSelection: function(id) {
            this.setState({selection: id});
        },

        render: function () {

            var selection = this.state.selection
                ? <div><h3>Selected:</h3><TapListContainer id={this.state.selection} /></div>
                : <div>Please select agent</div>

            return (
                <div>
                    <List handleSelection={this.handleSelection} />
                    {selection}
                </div>
            )
        }
    });

});