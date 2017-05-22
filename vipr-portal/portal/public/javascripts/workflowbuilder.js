
var folderNodeType = "FOLDER";
var workflowNodeType = "Workflow";
var shellNodeType = "script";
var localAnsibleNodeType = "ansible"
var restAPINodeType = "rest"
var viprRestAPINodeType = "vipr";
var remoteAnsibleNodeType = "remote_ansible"

angular.module("portalApp").controller('builderController', function($scope, $rootScope) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")
    $rootScope.$on("addWorkflowTab", function(event, id, name){
       addTab(id,name);
    });

    $scope.workflowTabs = {};
    $scope.isWorkflowTabsEmpty = function () {
        return $.isEmptyObject($scope.workflowTabs);
    };

    function addTab(id,name) {
        var elementid = id.replace(/:/g,'');
        $scope.workflowTabs[elementid] = { id:id, elementid:elementid, name:name, href:'#'+elementid };
    }
    $scope.closeTab = function(tabID){
        delete $scope.workflowTabs[tabID];
        $(".workflow-nav-tabs li").children('a').first().click();
    };
})
.controller('treeController', function($element, $scope, $compile, $http, $rootScope, translate) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    $scope.libOpen = true;
    $scope.toggleLib = function() {
        $("#builderController").toggleClass("collapsedBuilder");
        $("#libSidebar").toggleClass("collapsedSideBar");

        $scope.libOpen = !$scope.libOpen;
    }

    var jstreeContainer = $element.find('#jstree_demo');

    var fileNodeTypes = [shellNodeType, localAnsibleNodeType, remoteAnsibleNodeType, restAPINodeType, workflowNodeType]
    var primitiveNodeTypes = [shellNodeType, localAnsibleNodeType, remoteAnsibleNodeType, restAPINodeType]
    var viprLib = "viprLib";
    var myLib = "myLib";

    initializeJsTree();

    function initializeJsTree(){
        var to = null;
        var searchElem = $element.find(".search-input");
        searchElem.keyup(function() {
            if(to) { clearTimeout(to); }
                to = setTimeout(function() {
                  var searchString = searchElem.val();
                  jstreeContainer.jstree('search', searchString);
                }, 250);
        });

        jstreeContainer.jstree({
            "core": {
                "animation": 0,
                "check_callback": true,
                "themes": {"stripes": false, "ellipsis": true},
                "data": {
                    "url" : "getWFDirectories",
                    "type":"get"
                }
            },
            "types": {
                "#": {
                    "max_children": 1,
                    "max_depth": 4,
                    "valid_children": ["root"]
                },
                "root": {
                    "icon": "glyphicon glyphicon-folder-close",
                    "valid_children": ["default"]
                },
                "FOLDER": {
                    "icon": "builder-jstree-icon builder-folder-icon",
                    "valid_children": ["Workflow","FOLDER", "script", "ansible", "rest", "remote_ansible"]
                },
                "Workflow": {
                    "icon": "builder-jstree-icon builder-jstree-workflow-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "script": {
                    "icon": "builder-jstree-icon builder-jstree-script-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "ansible": {
                    "icon": "builder-jstree-icon builder-jstree-ansible-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "remote_ansible": {
                    "icon": "builder-jstree-icon builder-jstree-remote-ansible-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "vipr": {
                    "icon": "builder-jstree-icon builder-jstree-vipr-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "rest": {
                    "icon": "builder-jstree-icon builder-jstree-rest-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                }
            },
            "plugins": [
                "search", "state", "types", "wholerow", "themes"
            ],
            "search" : {
                  'case_sensitive' : false,
                  'show_only_matches' : true
            }
        }).on('ready.jstree', function() {
            jstreeContainer.find( ".draggable-card" ).draggable({delay: 200,handle: "a",scroll: false,helper: getDraggableStepHTML,appendTo: 'body',cursorAt: { top: 8, left: -16 }});
        }).bind("rename_node.jstree clear_search.jstree search.jstree open_node.jstree", function() {
            jstreeContainer.find( ".draggable-card" ).draggable({delay: 200,handle: "a",scroll: false,helper: getDraggableStepHTML,appendTo: 'body',cursorAt: { top: 0, left: 0 }});
        }).on('search.jstree', function (nodes, str) {
              if (str.nodes.length === 0) {
                  $('#jstree_demo').css("visibility", "hidden");
              }
              else {
                $('#jstree_demo').css("visibility", "visible");
              }
        });
    }

    var getDraggableStepHTML= function(event){
        var stepName=event.target.text;
        var treeId = event.target.parentElement.id
        if (!stepName) {
            stepName=event.target.parentElement.text;
            treeId = event.target.parentElement.parentElement.id
        }
        var $item = '<div style="z-index:999;"class="item"><div class="itemText">' + stepName + '</div></div>';

        var itemData = jstreeContainer.jstree(true).get_json(treeId).data;
        // Data is not populated for workflows. So setting required fields here.
        if($.isEmptyObject(itemData)) {
            itemData = {"friendlyName":stepName,"type":workflowNodeType};
        }
        $rootScope.primitiveData = itemData;

        return $( $item );
    }

    // jstree actions
    //TODO: do error handling on all actions
    jstreeContainer.on("rename_node.jstree", renameDir);
    jstreeContainer.on("select_node.jstree", selectDir);
    jstreeContainer.on("hover_node.jstree", hoverDir);
    jstreeContainer.on("dehover_node.jstree", dehoverDir);

    function displaySuccessMessage(successMessage) {
    	var alertsDiv = $("#wfAlertsDiv");
    	var successAlert = alertsDiv.find("#alerts_success");
    	if(!successAlert.length) {
    	    // if it doesn't exist, create
    	    var successAlertHTML = `
    	    <p id="alerts_success" class="alert alert-success">
                <button type="button" class="close" data-dismiss="alert" aria-hidden="true">×</button>
                <span class="message"></span>
            </p>
    	    `;
    	    alertsDiv.append(successAlertHTML);
    	    successAlert = $("#alerts_success");
    	}

    	successAlert.find("span").html(successMessage);
        successAlert.show();
    };

    function displayErrorMessage(errorMessage) {
    	var alertsDiv = $("#wfAlertsDiv");
    	var errorAlert = alertsDiv.find("#alerts_error");
    	if(!errorAlert.length) {
    	    // if it doesn't exist, create
    	    var errorAlertHTML = `
    	    <p id="alerts_error" class="alert alert-danger">
                <button type="button" class="close" data-dismiss="alert" aria-hidden="true">×</button>
                <span class="message"></span>
            </p>
    	    `;
    	    alertsDiv.append(errorAlertHTML);
    	    errorAlert = $("#alerts_error");
    	}

    	errorAlert.find("span").html(errorMessage);
        errorAlert.show();
    };

    function createDir(event, data) {
        if (folderNodeType === data.node.type) {
            $http.get(routes.WF_directory_create({"name": data.node.text,"parent": data.node.parent})).success(function(resp) {
                data.instance.set_id(data.node, resp.id);
                displaySuccessMessage(translate("node.create.success"));
            })
            .error(function (error){
                deleteNodeFromJSTreeAndDisplayErrorMsg(data.node, error.details);
            });
        }
        else {
            $http.get(routes.Workflow_create({"workflowName": data.node.text,"dirID": data.node.parent})).success(function(resp) {
                data.instance.set_id(data.node, resp.id);
                displaySuccessMessage(translate("node.create.success"));
            })
            .error(function (error){
                deleteNodeFromJSTreeAndDisplayErrorMsg(data.node, error.details);
            });
        }
    };

    function deleteNodeFromJSTreeAndDisplayErrorMsg(selectedNode, errorMsg) {
        jstreeContainer.jstree(true).delete_node(selectedNode);
        displayErrorMessage(errorMsg);
    }

    function deleteNodeFromJSTreeAndDisplaySuccessMsg(jstreeRef, selectedNode) {
        jstreeRef.delete_node(selectedNode);

        // By default select "My Library"
        jstreeContainer.jstree("select_node", myLib);

        displaySuccessMessage(translate("node.delete.success"));
    }

    $scope.deleteNode = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected('full',true);
        if(!sel.length) { return false; }
        var nodeId = sel[0].id;
        var nodeType = sel[0].type;
        var nodeParent = sel[0].parent;
        if (folderNodeType === nodeType) {
            $http.get(routes.WF_directory_delete({"id": nodeId})).success(function() {
                deleteNodeFromJSTreeAndDisplaySuccessMsg(ref, sel);
            })
            .error(function (error){
                displayErrorMessage(error.details);
            });
        }
        else if($.inArray(nodeType, primitiveNodeTypes) > -1) {
        	$http.get(routes.Primitive_delete({"primitiveId": nodeId, "dirID": nodeParent})).success(function() {
                deleteNodeFromJSTreeAndDisplaySuccessMsg(ref, sel);
            })
            .error(function (error){
                displayErrorMessage(error.details);
            });
        }
        else {
            $http.get(routes.Workflow_delete({"workflowID": nodeId, "dirID": nodeParent})).success(function() {
                deleteNodeFromJSTreeAndDisplaySuccessMsg(ref, sel);
            })
            .error(function (error){
                displayErrorMessage(error.details);
            });
        }

    };

    function revertRename(node, oldText, errorMessage) {
        jstreeContainer.jstree('set_text', node , oldText );
        displayErrorMessage(errorMessage);
        addMoreOptions(node.id, node.type, node.parent);
    }

    function renameDir(event, data) {
        // Identifying if node is not saved to DB yet and creating it.
        if (!(data.node.id).startsWith("urn")) {
            createDir(event, data);
            addMoreOptions(data.node.parent, folderNodeType, "");
        }
        else {
            // if Old text is not equal to new text, then rename
            if (data.old !== data.text) {
                if (folderNodeType === data.node.type) {
                    $http.get(routes.WF_directory_edit_name({"id": data.node.id, "newName": data.text})).success(function() {
                        displaySuccessMessage(translate("node.rename.success"));
                    })
                    .error(function (error){
                        revertRename(data.node, data.old, error.details)
                    });
                }
                else if($.inArray(data.node.type, primitiveNodeTypes) > -1) {
                    $http.get(routes.Primitive_edit_name({"primitiveID": data.node.id, "newName": data.text})).success(function() {
                        displaySuccessMessage(translate("node.rename.success"));
                    })
                    .error(function (error){
                        revertRename(data.node, data.old, error.details)
                    });
                }
                else if (workflowNodeType === data.node.type){
                    $http.get(routes.Workflow_edit_name({"id": data.node.id, "newName": data.text})).success(function() {
                        displaySuccessMessage(translate("node.rename.success"));
                    })
                    .error(function (error){
                        revertRename(data.node, data.old, error.details)
                    });
                }
            }

            addMoreOptions(data.node.id, data.node.type, data.node.parent);
        }
    };


    var optionsHTML = `
    <div id="treeMoreOptionsSel" class="btn-group treeMoreOptions">
       <button id="optionsBtn" type="button" class="btn btn-xs btn-default dropdown-toggle" title="Options" data-toggle="dropdown">
           <span class="glyphicon"><img src="/public/img/customServices/ThreeDotsMenu.svg" height="20" width="20"></span>
       </button>
       <ul class="dropdown-menu dropdown-menu-right" role="menu">
            <li id="editMenu" style="display:none;"><a  href="#" ng-click="editNode();">${translate('wfBuilder.menu.edit')}</a></li>
            <li id="editWFMenu" style="display:none;"><a  href="#" ng-click="openWorkflow();">${translate('wfBuilder.menu.edit')}</a></li>
            <li id="renameMenu" style="display:none;"><a  href="#" ng-click="renameNode();">${translate('wfBuilder.menu.rename')}</a></li>
            <li id="deleteMenu" style="display:none;"><a  href="#" ng-click="deleteNode();">${translate('wfBuilder.menu.delete')}</a></li>
            <li id="importExportSeparator" role="separator" class="divider" style="display:none;"></li>
            <li id="exportMenu" style="display:none;"><a  href="#" ng-click="exportWorkflow();">${translate('wfBuilder.menu.export')}</a></li>
       </ul>
    </div>
    `;

    var validActionsOnMyLib = ["addWorkflowMenu", "addShellMenu", "addLAMenu", "addRestMenu", "addFolderDivider", "addFolderMenu"]
    var validActionsOnFolder = ["editDivider", "renameMenu", "deleteMenu"]
    var validActionsOnWorkflow = ["renameMenu", "editWFMenu", "deleteMenu", "exportMenu", "importExportSeparator"]
    var validActionsOnMyPrimitives = ["renameMenu", "deleteMenu", "editMenu"]

    function showOptions(nodeId, parents) {
        // Do not show 'More options' on ViPR Library nodes & My Library
        if($.inArray(viprLib, parents) > -1 || viprLib === nodeId || myLib === nodeId ) {
            return false;
        }
        return true;
    }

    function addMoreOptions(nodeId, nodeType, parents) {
        //remove any previous element
        $(".treeMoreOptions").remove();

        if(!showOptions(nodeId, parents)) return;

        //find anchor with this id and append "more options"
        $('[id="'+nodeId+'"]').children('a').before(optionsHTML);

        // If current node is vipr library or its parent is vipr library, disable all
        if(workflowNodeType === nodeType){
            // For workflows
            validActions = validActionsOnWorkflow
        }
        else if($.inArray(nodeType, fileNodeTypes) > -1){
            // For other file types (shell, rest, ansible)
            validActions = validActionsOnMyPrimitives;
        }
        else {
            // Other folders in My Library
            validActions = validActionsOnFolder;
        }

        // Show all validActions
        $.each(validActions, function( index, value ) {
            $('#'+value).show();
        });

        //TODO: check if we can avoid this search on ID
        var generated = jstreeContainer.jstree(true).get_node(nodeId, true);
        $compile(generated.contents())($scope);
    }


    function selectDir(event, data) {
        $scope.selNodeId = data.node.id;
        addMoreOptions(data.node.id, data.node.type, data.node.parents);

        // If current node is vipr library or its parent is vipr library, disable all
        if(viprLib === data.node.id || $.inArray(viprLib, data.node.parents) > -1 || $.inArray(data.node.type, fileNodeTypes) > -1) {
            // ViPR Library nodes - disable all buttons
            $('#addWorkflow').prop("disabled",true);
        }
        else {
            $('#addWorkflow').prop("disabled",false);
        }
    };

    $scope.hoverOptionsClick = function(nodeId){
        jstreeContainer.jstree("deselect_node", $scope.selNodeId);
        jstreeContainer.jstree("select_node", nodeId);
        event.stopPropagation();
        $("#optionsBtn").click();
    }

    function hoverDir(event, data) {
        var nodeId = data.node.id;
        $scope.hoverNodeId = nodeId;

        // Do not show again for selected node
        if (showOptions(nodeId, data.node.parents) && $scope.selNodeId !== nodeId) {
            var optionsHoverHTML = `
                <div id="treeMoreOptionsHover" class="btn-group treeMoreOptions">
                   <button id="optionsHoverBtn" type="button" class="btn btn-xs btn-default" title="Options" ng-click="hoverOptionsClick('${nodeId}');">
                       <span class="glyphicon"><img src="/public/img/customServices/ThreeDotsMenu.svg" height="20" width="20"></span>
                   </button>
                </div>
            `;

            $('[id="'+nodeId+'"]').children('a').before(optionsHoverHTML);
            var generated = jstreeContainer.jstree(true).get_node(nodeId, true);
            $compile(generated.contents())($scope);
        }

    }

    function dehoverDir(event, data) {
        //remove hover options
        $("#treeMoreOptionsHover").remove();
    }

    // Methods for JSTree actions
    $scope.addFolder = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected();
        if(!sel.length) { return false; }
        sel = sel[0];
        sel = ref.create_node(sel, {"type":folderNodeType});
        if(sel) {
            ref.edit(sel);
        }
    }

    $scope.addWorkflow = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected();
        if(!sel.length) { return false; }
        sel = sel[0];
        sel = ref.create_node(sel, {"type":workflowNodeType});
        if(sel) {
            ref.edit(sel);
        }
    }

    $scope.openShellScriptModal = function(){
        var scope = angular.element($('#scriptModal')).scope();
        scope.populateModal(false);
        $('#shellPrimitiveDialog').modal('show');
    }

    $scope.openLocalAnsibleModal = function(){
        var scope = angular.element($('#localAnsibleModal')).scope();
        scope.populateModal(false);
        $('#localAnsiblePrimitiveDialog').modal('show');
    }

    $scope.openRemoteAnsibleModal = function(){
        var scope = angular.element($('#remoteAnsibleModal')).scope();
        scope.populateModal(false);
        $('#remoteAnsiblePrimitiveDialog').modal('show');
    }

    $scope.openRestAPIModal = function(){
            var scope = angular.element($('#restAPIModal')).scope();
            scope.populateModal(false);
            $('#restAPIPrimitiveDialog').modal('show');
    }

    // Rename node
    $scope.renameNode = function(){
        var ref = jstreeContainer.jstree(true),
                sel = ref.get_selected('full',true);
        sel = sel[0];
        ref.edit(sel.id);
    }

    // if folder edit name, if primitive - open modal
    $scope.editNode = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected('full',true);

        if(!sel.length) { return false; }
        sel = sel[0];
        if(shellNodeType === sel.type) {
            //open script modal
            var scope = angular.element($('#scriptModal')).scope();
            scope.populateModal(true, sel.id, sel.type);
            $('#shellPrimitiveDialog').modal('show');
        }
        else if(localAnsibleNodeType === sel.type){
            //open script modal
            var scope = angular.element($('#localAnsibleModal')).scope();
            scope.populateModal(true, sel.id, sel.type);
            $('#localAnsiblePrimitiveDialog').modal('show');
        }
        else if(remoteAnsibleNodeType === sel.type){
            //open script modal
            var scope = angular.element($('#remoteAnsibleModal')).scope();
            scope.populateModal(true, sel.id, sel.type);
            $('#remoteAnsiblePrimitiveDialog').modal('show');
        }
        else if(restAPINodeType === sel.type){
            //open script modal
            var scope = angular.element($('#restAPIModal')).scope();
            scope.populateModal(true, sel.id, sel.type);
            $('#restAPIPrimitiveDialog').modal('show');
        }
        else{
            ref.edit(sel.id);
        }
    };

    $scope.openWorkflow = function() {
        var selectedNode = jstreeContainer.jstree(true).get_selected(true)[0];
        $rootScope.$emit("addWorkflowTab", selectedNode.id ,selectedNode.text);
    }

    $scope.exportWorkflow = function() {
        var selectedNode = jstreeContainer.jstree(true).get_selected(true)[0];
        $('#exportWorkflowId').val(selectedNode.id);
        $('#exportWorkflowForm').submit();
    }

    $scope.importWorkflow = function() {
        $('#importWorkflow_wfDirID').val(jstreeContainer.jstree(true).get_selected()[0]);
        $('#importWorkflowDialog').modal('show');
    }
})

.controller('tabController', function($element, $scope, $compile, $http, $rootScope, translate) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    var diagramContainer = $element.find('#diagramContainer');
    var sbSite = $element.find('#sb-site');
    var jspInstance;

    var INPUT_FIELD_OPTIONS = ['text','number','boolean','password'];
    var INPUT_TYPE_OPTIONS_REQUIRED = ['InputFromUser','AssetOptionMulti','AssetOptionSingle','InputFromUserMulti','FromOtherStepOutput','FromOtherStepInput'];
    var INPUT_TYPE_OPTIONS = ['Disabled'];
    var ASSET_TYPE_OPTIONS = ['assetType.vipr.blockVirtualPool','assetType.vipr.virtualArray','assetType.vipr.project','assetType.vipr.host'];

    $scope.workflowData = {};
    $scope.stepInputOptions = {};
    $scope.stepOutputOptions = {};
    var DEFAULT_OUTPUT_PARAMS = ["operation_output","operation_error","operation_returncode"];

    $scope.modified = false;
    $scope.showAlert = false;
    $scope.selectedId = '';
    $scope.detached = false;

    initializeJsPlumb();
    initializePanZoom();

    function activateTab(tab){
        $('.nav-tabs a[href="#' + tab + '"]').tab('show');
        loadJSON();
        $scope.modified = false;
    };

    $scope.initializeWorkflowData = function(workflowInfo) {
        var elementid = workflowInfo.id.replace(/:/g,'');
        $http.get(routes.Workflow_get({workflowId: workflowInfo.id})).then(function (resp) {
            if (resp.status == 200) {
                $scope.workflowData = resp.data;
                activateTab(elementid);
            } else {
                //TODO: show error for workflow failed to load
            }
        });
    }

    function initializePanZoom(){

        var widthHalf = (window.innerWidth / 2) - 75;
        var heightHalf = (window.innerHeight / 2) - 75;

        var $panzoom = diagramContainer.panzoom({
            cursor: "default",
            minScale: 0.5,
            maxScale: 2,
            increment: 0.1,
            duration: 100
            //TODO add contain: 'invert'
        });

        //DOMMouseScroll is needed for firefox
        $panzoom.parent().on('mousewheel.focal DOMMouseScroll', function(e) {
            e.preventDefault();
            var delta = e.delta || e.originalEvent.wheelDeltaY;
            var focalPoint = e;

            //if delta is null then DOMMouseScroll was used,
            //we can map important data to similar delta/focalpoint objects
            if (!delta){
                delta = e.originalEvent.detail;
                focalPoint = {
                    clientX: e.originalEvent.clientX,
                    clientY: e.originalEvent.clientY
                };
            }
            if (delta !== 0) {
                var zoomOut = delta < 0;
                $panzoom.panzoom('zoom', zoomOut, {
                    animate: false,
                    increment: 0.1,
                    focal: focalPoint
                });
            }
        });
        $panzoom.panzoom("pan", -2000 + widthHalf, -2000 + heightHalf, {
            relative: false
        });
        $panzoom.on('panzoomzoom', function(e, panzoom, scale) {
            jspInstance.setZoom(scale);
        });

        var str_down = 'mousedown' + ' pointerdown' + ' MSPointerDown';
        var str_start = 'touchstart' + ' ' + str_down;

        diagramContainer.on(str_start, "*", function(e) {
            e.stopImmediatePropagation();
        });
    }

    function initializeJsPlumb(){
        jspInstance = jsPlumb.getInstance();
        jspInstance.importDefaults({
            DragOptions: {
                cursor: "none"
            },
            ConnectionOverlays: [
                ["PlainArrow", {
                    location: 1,
                    visible:true,
                    id:"ARROW",
                    width: 12,
                    length: 12

                } ]]
        });
        jspInstance.setContainer(diagramContainer);
        jspInstance.setZoom(1);
        sbSite.droppable({drop: dragEndFunc});
        setBindings($element);
    }

    /*
    Shared Endpoint params for each step
    */
    var passEndpoint = {
        endpoint: ["Image", {
            src:"/public/img/customServices/YesDark.svg"
        }],
        isSource: true,
        connector: ["Flowchart", {
            cornerRadius: 5
        }],
        anchors: [0.5, 1, 0, 1,0,10],
		connectorStyle:{ strokeStyle:"#3fac49", lineWidth:1 },
        cssClass: "passEndpoint"
    };

    var failEndpoint = {
        endpoint: ["Image", {
            src:"/public/img/customServices/NoDark.svg"
        }],
        isSource: true,
        connector: ["Flowchart", {
            cornerRadius: 5
        }],
        anchors: [1, 0.5, 1, 0,10,0],
        connectorStyle:{ strokeStyle:"#ee3825", lineWidth:1 },
        cssClass: "failEndpoint"
    };

    var targetParams = {
        anchors: ["Top","Left"],
        endpoint: "Blank",
        filter:":not(a)"
    };


    /*
    Functions for managing step data on jsplumb instance
    */
    function dragEndFunc(e) {
        //set ID and text within the step element
        //TODO: retrieve stepname from step data when API is available
        var randomIdHash = Math.random().toString(36).substring(7);

        //compensate x,y for zoom
        var x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
        var y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
        var scaleMultiplier = 1 / jspInstance.getZoom();;
        var positionY = (y - diagramContainer.offset().top) * scaleMultiplier;
        var positionX = (x - diagramContainer.offset().left) * scaleMultiplier;


        //add data
        var stepData = $rootScope.primitiveData;
        stepData.operation = stepData.id;
        stepData.id = randomIdHash;
        stepData.positionY = positionY;
        stepData.positionX = positionX;

        // Add default params
        if (!stepData.output) {
            stepData.output = [];
        }

        $scope.modified = true;
        loadStep(stepData);

    }

    $scope.getInputOptions=function(){
        return Object.keys($scope.stepInputOptions).map(function(key) {
               return $scope.stepInputOptions[key];
           });
    }

    $scope.getOutputOptions=function(){
        return Object.keys($scope.stepOutputOptions).map(function(key) {
               return $scope.stepOutputOptions[key];
           });
    }

    function setBindings() {
        jspInstance.bind("connection", function(connection) {
            var source=$(connection.source);
            var sourceEndpoint=$(connection.sourceEndpoint.canvas);
            var sourceData = source.data("oeData");
            var sourceNext = {};

            if (sourceData.next) {
                sourceNext = sourceData.next;
            }
            if (sourceEndpoint.hasClass("passEndpoint")) {
                sourceNext.defaultStep=connection.targetId
            }
            if (sourceEndpoint.hasClass("failEndpoint")) {
                sourceNext.failedStep=connection.targetId
            }
            // Populate array for input and output from previous steps

            if (sourceData.id !== 'Start' && sourceData.id !== 'End' ){
                if("inputGroups" in sourceData) {
                    for(var inputGroupEntry in sourceData.inputGroups) {
                        if(sourceData.inputGroups.hasOwnProperty(inputGroupEntry)) {
                            var inparams = sourceData.inputGroups[inputGroupEntry].inputGroup;
                            for(var inputparam in inparams) {
                                if(inparams.hasOwnProperty(inputparam)) {
                                    var inparam_name = inparams[inputparam].name;
                                    var stepidconcate = sourceData.id + "." + inparam_name;
                                    var stepnameconcate = sourceData.friendlyName + " " + inparam_name
                                    //prevents duplicates by using key
                                    $scope.stepInputOptions[stepidconcate]={id:stepidconcate, name:stepnameconcate};
                                }
                            }
                        }
                    }
                }
                var outparams = sourceData.output;
                for(var outputparam in outparams) {
                    if(outparams.hasOwnProperty(outputparam)) {
                        var outparam_name = outparams[outputparam].name;
                        var stepidconcate = sourceData.id + "." + outparam_name;
                        var stepnameconcate = sourceData.friendlyName + " " + outparam_name
                        $scope.stepOutputOptions[stepidconcate]={id:stepidconcate, name:stepnameconcate};
                    }
                }

                for(var outputparam in DEFAULT_OUTPUT_PARAMS) {
                    if(DEFAULT_OUTPUT_PARAMS.hasOwnProperty(outputparam)) {
                        var outparam_name = DEFAULT_OUTPUT_PARAMS[outputparam];
                        var stepidconcate = sourceData.id + "." + outparam_name;
                        var stepnameconcate = sourceData.friendlyName + " " + outparam_name
                        $scope.stepOutputOptions[stepidconcate]={id:stepidconcate, name:stepnameconcate};
                    }
                }
            }
            sourceData.next=sourceNext;
            $scope.modified = true;
            $scope.$apply();
        });

        jspInstance.bind("connectionDetached", function(connection) {
            var source=$(connection.source);
            var sourceEndpoint=$(connection.sourceEndpoint.canvas);
            var sourceData = source.data("oeData");
            var sourceNext = {};
            if (sourceData.next) {
                sourceNext = sourceData.next;
            }
            if (sourceEndpoint.hasClass("passEndpoint")) {
                delete sourceData.next.defaultStep;
            }
            if (sourceEndpoint.hasClass("failEndpoint")) {
                delete sourceData.next.failedStep;
            }
            sourceData.next=sourceNext;
            // Remove source data after unbind array for input and output from previous steps
            if (!sourceData.next.hasOwnProperty("defaultStep") && !sourceData.next.hasOwnProperty("failedStep")){
                 for (var k in $scope.stepInputOptions){
                    if ($scope.stepInputOptions.hasOwnProperty(k)) {
                        if (k.startsWith(sourceData.id + ".")) {
                            delete $scope.stepInputOptions[k];
                        }
                    }
                }
                 for (var k in $scope.stepOutputOptions){
                    if ($scope.stepOutputOptions.hasOwnProperty(k)) {
                        if (k.startsWith(sourceData.id + ".")) {
                            delete $scope.stepOutputOptions[k];
                        }
                    }
                }
            }
            $scope.modified = true;
            $scope.$apply();
        });

        jspInstance.bind("connectionDrag", function(connection) {
            //
            if(!$('#'+connection.targetId).hasClass('item')) {
                //Hide Endpoint logic
                $( '.jsplumb-endpoint' ).each(function( index, item ) {
                        if (!$(item).hasClass('jsplumb-endpoint-connected')){
                            $(item).css( "opacity", "0" );
                        }
                    });

                //Glow logic
                /*if($(connection.getAttachedElements()[0].canvas).hasClass('passEndpoint')){
                    $( '.item , #End' ).each(function( index, item ) {
                        if (item.id !== connection.getAttachedElements()[0].id){
                            if (item.id !== connection.sourceId){
                                $(item).addClass('glow-pass');
                            }
                        }
                    });
                }
                if($(connection.getAttachedElements()[0].canvas).hasClass('failEndpoint')){
                    $( '.item , #End' ).each(function( index, item ) {
                        if (item.id !== connection.sourceId){
                            $(item).addClass('glow-fail');
                        }
                    });
                }*/
            }
        });

        jspInstance.bind("connectionDragStop", function(connection) {
            //Hide Endpoint logic
            $( '.jsplumb-endpoint' ).each(function( index, item ) {
                    $(item).css( "opacity", "" );
            });

            //Glow logic
            /*$( '.item , #End' ).each(function( index, item ) {
                $(item).removeClass('glow-pass');
                $(item).removeClass('glow-fail');
            });*/
        });

        jspInstance.bind("beforeDrop", function (info) {
        // No self connections
            if (info.sourceId === info.targetId) { //source and target ID's are same
                return false;
            } else {
                return true;
            }
        });
    }

    $scope.setWorkflowModified = function (state) {
        $scope.modified = state;
    }

    function buildJSON() {
        var blocks = []
        diagramContainer.find(" .item,  .item-start-end").each(function(idx, elem) {
            var $elem = $(elem);
            var $wrapper = $elem.parent();
            var data = $elem.data("oeData");
            delete data.$classCounts;
            blocks.push($.extend(data,{
                positionX: parseInt($wrapper.css("left"), 10),
                positionY: parseInt($wrapper.css("top"), 10)
            } ));
        });

        //TODO: return JSON data so that it can be accessed in Export/SaveWorkflow via this method
        $scope.workflowData.document.steps = blocks;
    }

    $scope.saveWorkflow = function() {
        $scope.workflowData.state = 'SAVING';
        buildJSON();
        $http.post(routes.Workflow_save({workflowId : $scope.workflowData.id}),{workflowDoc : $scope.workflowData.document}).then(function (resp) {
            updateWorkflowData(resp,function(){
                $scope.modified = false;
            });
        },
        function(resp){
            $scope.showAlert = true;
            $scope.alert = {status : "INVALID", error : {errorMessage : "An unexpected error occurred while saving the workflow."}};
            if (resp.data.details){
                $scope.alert.error.details = resp.data.details;
            }
            $scope.workflowData.state = 'INVALID';
        });
    }

    function updateWorkflowData(resp,successCallback){
        $scope.workflowData.state = resp.data.state;
        if (successCallback) successCallback();
    }

    $scope.validateWorkflow = function() {
        $scope.workflowData.state = 'VALIDATING';
        delete $scope.alert;
        $http.post(routes.Workflow_validate({workflowId : $scope.workflowData.id})).then(function (resp) {
            $scope.workflowData.state = resp.data.status;
            if (resp.data.status == "INVALID") {
                $scope.showAlert = true;
                $scope.alert = resp.data;
                if ($scope.alert.error) {
                    if (!$scope.alert.error.errorMessage) {
                        $scope.alert.error.errorMessage='Workflow validation failed. There are '+Object.keys(resp.data.error.errorSteps).length+' steps with errors.';
                    }
                }
            } else {
                $scope.showAlert = true;
                $scope.alert = {status : "SUCCESS",success : {successMessage : "Workflow Validated Successfully."}};
            }
        },
        function(){
            $scope.showAlert = true;
            $scope.alert = {status : "INVALID", error : {errorMessage : "An unexpected error occurred while validating the workflow."}};
            $scope.workflowData.state = 'INVALID';
        });
    }

    $scope.testWorkflow = function() {
        $scope.workflowData.state = 'TESTING';
        delete $scope.alert;
        var url = routes.ServiceCatalog_showService({serviceId: $scope.workflowData.id});
        window.location.href = url;
    }

    $scope.publishorkflow = function() {
        $scope.workflowData.state = 'PUBLISHING';
        $http.post(routes.Workflow_publish({workflowId : $scope.workflowData.id})).then(function (resp) {
            //redirect automatically on success
            var url = routes.ServiceCatalog_createServiceFromBase({baseService: resp.data.name});
            window.location.href = url;
        });
    }

    $scope.unpublishWorkflow = function() {
        $scope.workflowData.state = 'UNPUBLISHING';
        $http.post(routes.Workflow_unpublish({workflowId : $scope.workflowData.id})).then(function (resp) {
            updateWorkflowData(resp);
        });
    }

    $scope.removeStep = function(stepId) {
        if($scope.selectedId===stepId){
            $scope.selectedId='';
            $scope.closeMenu();
        }
        jspInstance.remove(diagramContainer.find('#' + stepId+'-wrapper'));
        $scope.modified = true;
    }

    $scope.select = function(stepId) {
        $scope.selectedId = stepId;$scope.InputFieldOption=translateList(INPUT_FIELD_OPTIONS,'input.fieldType');
        $scope.AssetOptionTypes=translateList(ASSET_TYPE_OPTIONS,'input');
        var data = diagramContainer.find('#'+stepId).data("oeData");
        $scope.stepData = data;
        $scope.menuOpen = true;
        $scope.openPage(0);
    }

    $scope.getUserInputTypeOption = function(required) {
        if (required) {
            return translateList(INPUT_TYPE_OPTIONS_REQUIRED,'input.type');
        } else {
            return translateList(INPUT_TYPE_OPTIONS.concat(INPUT_TYPE_OPTIONS_REQUIRED),'input.type');
        }
    }

    /* creates list of objects for select one drop downs
     * translates key.id from messages file for the name
     */
    function translateList(idList,key) {
        var translateList = [];
        idList.forEach(function(id) {
            translateList.push({id:id, name:translate(key+'.'+id)});
        });
        return translateList;
    }

    $scope.getStepIconClass = function(stepType) {
        return getStepIconClass(stepType);
    }

	var draggableNodeTypes = {"shellNodeType":shellNodeType, "localAnsibleNodeType":localAnsibleNodeType, "remoteAnsibleNodeType":remoteAnsibleNodeType, "restAPINodeType":restAPINodeType, "viprRestAPINodeType":viprRestAPINodeType, "workflowNodeType":workflowNodeType}
    function getStepIconClass(stepType){
        var stepIconClass = "builder-step-icon";
        if(stepType != null) {
            switch(stepType.toLowerCase()){
                case draggableNodeTypes.shellNodeType:
                    stepIconClass = "builder-script-icon";
                    break;
                case draggableNodeTypes.localAnsibleNodeType:
                    stepIconClass = "builder-ansible-icon";
                    break;
                case draggableNodeTypes.remoteAnsibleNodeType:
                    stepIconClass = "builder-remote-ansible-icon";
                    break;
                case draggableNodeTypes.workflowNodeType:
                    stepIconClass = "builder-workflow-icon";
                    break;
                case draggableNodeTypes.restAPINodeType:
                    stepIconClass = "builder-rest-icon";
                    break;
                case draggableNodeTypes.viprRestAPINodeType:
                    stepIconClass = "builder-vipr-icon";
                    break;
            }
        }
        return stepIconClass;
    }

    $scope.hoverErrorIn = function(id) {
        $scope.alert.error.errorSteps[id].visible = true;
    }

    $scope.hoverErrorOut = function(id) {
        $scope.alert.error.errorSteps[id].visible = false;
    }

    $scope.hoverInputErrorIn = function(step,inputGroup,input) {
        $scope.alert.error.errorSteps[step].errorInputGroups[inputGroup].errorInputs[input].visible = true;
    }

    $scope.hoverInputErrorOut = function(step,inputGroup,input) {
        $scope.alert.error.errorSteps[step].errorInputGroups[inputGroup].errorInputs[input].visible = false;
    }

    $scope.getInputError = function(step,inputGroup,input) {
        if ('alert' in $scope && 'error' in $scope.alert && 'errorSteps' in $scope.alert.error && step in $scope.alert.error.errorSteps){
            var stepError = $scope.alert.error.errorSteps[step];
            if ('errorInputGroups' in stepError && inputGroup in stepError.errorInputGroups && 'errorInputs' in stepError.errorInputGroups[inputGroup] && input in stepError.errorInputGroups[inputGroup].errorInputs) {
                var inputError = stepError.errorInputGroups[inputGroup].errorInputs[input];
                return inputError;
            }
        }
    }

    $scope.checkStepErrorMessage = function(id) {
        var stepError = $scope.alert.error.errorSteps[id];
        if ('errorInputGroups' in stepError){
            var inputErrorCount = 0;
            for(var inputGroup in stepError.errorInputGroups) {
                if(stepError.errorInputGroups.hasOwnProperty(inputGroup)) {
                    if('errorInputs' in stepError.errorInputGroups[inputGroup]) {
                        inputErrorCount += Object.keys(stepError.errorInputGroups[inputGroup].errorInputs).length;
                    }
                }
            }
            if (inputErrorCount > 0){
                if (stepError.errorMessages) {
                    $scope.alert.error.errorSteps[id].errorMessages.push("Step has "+inputErrorCount+" input errors");
                } else {
                    $scope.alert.error.errorSteps[id].errorMessages = ["Step has "+inputErrorCount+" input errors"];
                }
            }
        }
    }

    function loadStep(step) {
        if(!step.positionY || !step.positionX){
            return;
        }

        var stepId = step.id;
        var stepName = step.friendlyName;

        //create element html
        var stepDivID = stepId + "-wrapper";
        var trimmedStepName = stepName;
        if (stepName.length > 70)
            trimmedStepName = stepName.substring(0,65)+'...';
        var stepHTML = `
        <div id="${stepDivID}" class="example-item-card-wrapper" ng-class="{\'highlighted\':(selectedId == '${stepId}' && menuOpen)}">
            <div ng-if="alert.error.errorSteps.${stepId}" ng-init="checkStepErrorMessage('${stepId}')" ng-class="{'visible':alert.error.errorSteps.${stepId}.visible}" class="custom-error-popover custom-error-step-popover top">
                <div class="arrow"></div><div ng-repeat="message in alert.error.errorSteps.${stepId}.errorMessages" class="custom-popover-content">{{message}}</div>
            </div>
            <span id="${stepId}-error"  class="glyphicon item-card-error-icon failure-icon" ng-if="alert.error.errorSteps.${stepId}" ng-mouseover="hoverErrorIn('${stepId}')" ng-mouseleave="hoverErrorOut('${stepId}')"></span>
            <div  class="button-container">
                <a class="glyphicon glyphicon-remove button-step-close" ng-click="removeStep('${stepId}')"></a>
                <a class="glyphicon glyphicon-pencil button-step-close" ng-click="select('${stepId}')"></a>
            </div>
            <div id="${stepId}"  class="item">
                <div class="step-type-image ${getStepIconClass(step.type)}">
                </div>
                <div class="itemText">${trimmedStepName}</div>
            </div>
        </div>
        `;

        if (stepId === "Start" || stepId === "End"){
            var stepSEHTML = `
            <div id="${stepDivID}" class="example-item-card-wrapper">
            <div ng-if="alert.error.errorSteps.${stepId}" style="bottom: 60px;" ng-init="checkStepErrorMessage('${stepId}')" ng-class="{'visible':alert.error.errorSteps.${stepId}.visible}" class="custom-error-popover custom-error-step-popover top">
                <div class="arrow"></div><div ng-repeat="message in alert.error.errorSteps.${stepId}.errorMessages" class="custom-popover-content">{{message}}</div>
            </div>
            <span id="${stepId}-error"  class="glyphicon glyphicon-remove-sign item-card-error-icon failure-icon" ng-if="alert.error.errorSteps.${stepId}" ng-mouseover="hoverErrorIn('${stepId}')" ng-mouseleave="hoverErrorOut('${stepId}')"></span>
                <div id="${stepId}"  class="item-start-end" ng-class="{\'highlighted\':selectedId == '${stepId}'}">
                    <div class="itemTextStartEnd">${stepName}</div>
                </div>
            </div>
            `;
            $(stepSEHTML).appendTo(diagramContainer);
        } else {
            $(stepHTML).appendTo(diagramContainer);
        }
        var theNewItemWrapper = diagramContainer.find(' #' + stepId+'-wrapper');
        var theNewItem = diagramContainer.find(' #' + stepId);

        //add data
        if(!step.operation) {step.operation = step.name}
        theNewItem.data("oeData",step);

        //set position of element
        $(theNewItemWrapper).css({
            'top': step.positionY,
            'left': step.positionX
        });

        //add jsPlumb options
        if (stepId !== "Start"){
            jspInstance.makeTarget(diagramContainer.find(' #'+stepId), targetParams);
        }
        if(stepId !== "End"){
            jspInstance.addEndpoint(diagramContainer.find(' #'+stepId), {uuid:stepId+"-pass"}, passEndpoint);
        }
        if(stepId !== "Start" && stepId !== "End"){
            jspInstance.addEndpoint(diagramContainer.find(' #'+stepId), {uuid:stepId+"-fail"}, failEndpoint);
        }
        jspInstance.draggable(diagramContainer.find(' #'+stepId+'-wrapper'));

        //updates angular handlers for the new element
        $compile(theNewItemWrapper)($scope);
    }

    function loadConnections(step) {
        if(step.next){
            if(step.next.defaultStep){
                var passEndpoint = jspInstance.getEndpoint(step.id+"-pass");
                jspInstance.connect({source:passEndpoint, target:diagramContainer.find('#' + step.next.defaultStep), paintStyle:{ strokeStyle:"#3fac49", lineWidth:1 }});
            }
            if(step.next.failedStep){
                var failEndpoint = jspInstance.getEndpoint(step.id+"-fail");
                jspInstance.connect({source:failEndpoint, target:diagramContainer.find('#' + step.next.failedStep), paintStyle:{ strokeStyle:"#ee3825", lineWidth:1 }});
            }
        }
    }

    function loadJSON() {

        //load steps with position data
        $scope.workflowData.document.steps.forEach(function(step) {
            loadStep(step);
        });

        //load connections
        $scope.workflowData.document.steps.forEach(function(step) {
            loadConnections(step);
        });

    }

    $scope.reset = function() {
        jspInstance.deleteEveryEndpoint();
        diagramContainer.find(' .example-item-card-wrapper').each( function(idx,elem) {
            var $elem = $(elem);
            $elem.remove();
        });
        $scope.workflowData = {};
    }

    $scope.menuOpen = false;

    $scope.openPage = function(pageId){
        $scope.menuOpen = true;
    }

    $scope.toggleMenu = function(){
        $scope.menuOpen = !$scope.menuOpen;
    }

    $scope.closeMenu = function() {
        $scope.menuOpen = false;
    }

    $scope.closeAlert = function() {
        $scope.showAlert = false;
    }
});

