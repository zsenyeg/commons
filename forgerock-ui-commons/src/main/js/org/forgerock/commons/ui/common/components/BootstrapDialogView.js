/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

/*global define, $, _ */

define("org/forgerock/commons/ui/common/components/BootstrapDialogView", [
    "org/forgerock/commons/ui/common/main/AbstractView",
    "bootstrap-dialog",
    "org/forgerock/commons/ui/common/util/UIUtils"
], function(AbstractView, BootstrapDialog, UIUtils) {
    var BootstrapDialogView = AbstractView.extend({
        contentTemplate: "templates/common/DefaultBaseTemplate.html",
        data: { },
        noButtons: false,
        type: BootstrapDialog.TYPE_DEFAULT,
        size: BootstrapDialog.SIZE_NORMAL,
        closable : true,
        actions: [{
            label: function (){return $.t('common.form.close');},
            cssClass: "btn-default",
            type: "close"
        }],

        show: function(callback) {
            var self = this;
            self.setButtons();
            self.loadContent(function(content){
                self.message = $("<div></div>").append(content);
                BootstrapDialog.show(self);
                if (callback) {
                    callback();
                }
            });
        },

        loadContent: function(callback){
            if (this.message === undefined) {
                UIUtils.fillTemplateWithData(this.contentTemplate, this.data, function(template) {
                    callback(template);
                });
            } else {
                callback(this.message);
            }
        },

        setTitle: function(title) {
            this.title = title;
        },

        addButton: function(button){
            if (!this.getButtons(button.label)){
                this.buttons.push(button);
            }
        },

        getButtons: function(label) {
            return _.find(this.buttons, function(a) {
                return a.label === label;
            });
        },

        setButtons: function() {
            var buttons = [];
            if (this.noButtons) {
                this.buttons = [];
            } else if (this.actions !== undefined && this.actions.length !== 0){
                $.each (this.actions, function(i, action){
                    if (action.type === "close") {
                        action.label = $.t('common.form.close');
                        action.cssClass = (action.cssClass ? action.cssClass : "btn-default");
                        action.action = function(dialog) {
                            dialog.close();
                        };
                    }
                    buttons.push(action);
                });
                this.buttons = buttons;
            }
        }
    });

    return BootstrapDialogView;
});
