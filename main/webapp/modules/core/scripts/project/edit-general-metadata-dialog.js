

function EditGeneralMetadataDialog(projectId, callback) {
    this._projectId = projectId;
    this._callback = callback;
  this._createDialog();
  
  alert("_createDialog done");
}

EditGeneralMetadataDialog.prototype._createDialog = function() {
  var self = this;
  
  var frame = $(DOM.loadHTML("core", "scripts/project/edit-general-metadata-dialog.html"));
  this._elmts = DOM.bind(frame);  

  this._level = DialogSystem.showDialog(frame);
  this._elmts.okButton.html($.i18n._('core-buttons')["ok"]);
  this._elmts.okButton.click(function() { self._submit(); });
  this._elmts.closeButton.html($.i18n._('core-buttons')["close"]);
  this._elmts.closeButton.click(function() { self._dismiss(); });
  
  $('<h1>').text($.i18n._('core-index')["metaDatas"]).appendTo(body);
  var editor = new JSONEditor(document.getElementById('jsoneditor'));

  $.get(
          "command/core/get-imetaData",
          {
            project : this._projectId,
            metadataFormat : "DATAPACKAGE_METADATA"
          },
          function(o) {
            if (o.code === "error") {
              alert(o.message);
            } 
            editor.setText(JSON.stringify(o));
          },
          "json"
        );
  
  $(".dialog-container").css("top", Math.round(($(".dialog-overlay").height() - $(frame).height()) / 2) + "px");
};

EditGeneralMetadataDialog.prototype._dismiss = function() {
    DialogSystem.dismissUntil(this._level - 1);
};

EditGeneralMetadataDialog.prototype._submit = function() {
    if (typeof this._callback === "function") 
        this._callback();
    this._dismiss();
};