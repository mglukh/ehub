/** @jsx React.DOM */
define(['react', 'sendOnlyMixin'], function (React, sendOnlyMixin) {

    // use this.sendCommand(subject, data) to talk to server

    return React.createClass({

        mixins: [sendOnlyMixin],

        getInitialState: function () {
            return {connected: false, a: '12345'}
        },

        handleAdd: function(e) {
            var name = this.refs.gateName.getDOMNode().value.trim();
            if (!name) return;

            this.sendCommand("gates", "add", {name: name});

            this.refs.gateName.getDOMNode().value = null;

            $('#myModal').modal('hide');
            return true;
        },

        render: function () {

            var cx = React.addons.classSet;
            var buttonClasses = cx({
                'disabled': !this.state.connected
            });

            return (
              <div>
                  <button type="button" className={"btn btn-primary btn-sm " + buttonClasses} data-toggle="modal" data-target="#myModal" >
                  Add new
                  </button>

                  <div className="modal fade" id="myModal" tabIndex="-1" role="dialog" aria-labelledby="myModalLabel" aria-hidden="true">
                      <div className="modal-dialog">
                          <div className="modal-content">
                              <div className="modal-header">
                                  <button type="button" className="close" data-dismiss="modal"><span aria-hidden="true">&times;</span><span className="sr-only">Close</span></button>
                                  <h4 className="modal-title" id="myModalLabel">Modal title</h4>
                              </div>
                              <div className="modal-body">
                                  <input type="text" className="form-control" ref="gateName" placeholder="Gate name"/>
                              </div>
                              <div className="modal-footer">
                                  <button type="button" className="btn btn-default" data-dismiss="modal">Close</button>
                                  <button type="button" className="btn btn-primary" onClick={this.handleAdd}>Save changes</button>
                              </div>
                          </div>
                      </div>
                  </div>

              </div>
            );
        }
    });

});