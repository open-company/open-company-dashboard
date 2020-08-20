"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.TCMention = exports.LEFT_ARROW_KEYCODE = undefined;
exports.unwrapForTextNode = unwrapForTextNode;

var _mediumEditor = require("medium-editor");

var _mediumEditor2 = _interopRequireDefault(_mediumEditor);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function last(text) {
  return text[text.length - 1];
}

var LEFT_ARROW_KEYCODE = exports.LEFT_ARROW_KEYCODE = 37;

function unwrapForTextNode(el, doc) {
  var parentNode = el.parentNode;
  _mediumEditor2.default.util.unwrap(el, doc);
  // Merge textNode
  var currentNode = parentNode.lastChild;
  var prevNode = currentNode.previousSibling;
  while (prevNode) {
    if (currentNode.nodeType === 3 && prevNode.nodeType === 3) {
      prevNode.textContent += currentNode.textContent;
      parentNode.removeChild(currentNode);
    }
    currentNode = prevNode;
    prevNode = currentNode.previousSibling;
  }
}

var TCMention = exports.TCMention = _mediumEditor2.default.Extension.extend({
  name: "mention",

  /* @deprecated: use extraPanelClassName. Will remove in next major (3.0.0) release
   * extraClassName: [string]
   *
   * Extra className to be added with the `medium-editor-mention-panel` element.
   */
  extraClassName: "",

  /* @deprecated: use extraActivePanelClassName. Will remove in next major (3.0.0) release
   * extraActiveClassName: [string]
   *
   * Extra active className to be added with the `medium-editor-mention-panel-active` element.
   */
  extraActiveClassName: "",

  /* extraPanelClassName: [string]
   *
   * Extra className to be added with the `medium-editor-mention-panel` element.
   */
  extraPanelClassName: "",

  /* extraActivePanelClassName: [string]
   *
   * Extra active className to be added with the `medium-editor-mention-panel-active` element.
   */
  extraActivePanelClassName: "",

  extraTriggerClassNameMap: {},

  extraActiveTriggerClassNameMap: {},

  /* tagName: [string]
   *
   * Element tag name that would indicate that this mention. It will have
   * `medium-editor-mention-at` className applied on it.
   */
  tagName: "strong",

  /* renderPanelContent: [
   *    function (panelEl: dom, currentMentionText: string, selectMentionCallback: function)
   * ]
   *
   * Render function that used to create the content of the panel when panel is show.
   *
   * @params panelEl: DOM element of the panel.
   *
   * @params currentMentionText: Often used as query criteria. e.g. @medium
   *
   * @params selectMentionCallback:
   *    callback used in customized panel content.
   *
   *    When called with null, it tells the Mention plugin to close the panel.
   *        e.g. selectMentionCallback(null);
   *
   *    When called with text, it tells the Mention plugin that the text is selected by the user.
   *        e.g. selectMentionCallback("@mediumrocks")
   */
  renderPanelContent: function renderPanelContent() {},


  /* destroyPanelContent: [function (panelEl: dom)]
   *
   * Destroy function to remove any contents rendered by renderPanelContent
   *  before panelEl being removed from the document.
   *
   * @params panelEl: DOM element of the panel.
   */
  destroyPanelContent: function destroyPanelContent() {},


  activeTriggerList: ["@"],

  triggerClassNameMap: {
    "#": "medium-editor-mention-hash",
    "@": "medium-editor-mention-at"
  },

  activeTriggerClassNameMap: {
    "#": "medium-editor-mention-hash-active",
    "@": "medium-editor-mention-at-active"
  },

  hideOnBlurDelay: 300,

  init: function init() {
    this.initMentionPanel();
    this.attachEventHandlers();
  },
  destroy: function destroy() {
    this.detachEventHandlers();
    this.destroyMentionPanel();
  },
  initMentionPanel: function initMentionPanel() {
    var el = this.document.createElement("div");

    el.classList.add("medium-editor-mention-panel");
    if (this.extraPanelClassName || this.extraClassName) {
      el.classList.add(this.extraPanelClassName || this.extraClassName);
    }

    this.getEditorOption("elementsContainer").appendChild(el);

    this.mentionPanel = el;
  },
  destroyMentionPanel: function destroyMentionPanel() {
    if (this.mentionPanel) {
      if (this.mentionPanel.parentNode) {
        this.destroyPanelContent(this.mentionPanel);
        this.mentionPanel.parentNode.removeChild(this.mentionPanel);
      }
      delete this.mentionPanel;
    }
  },
  attachEventHandlers: function attachEventHandlers() {
    var _this = this;

    this.unsubscribeCallbacks = [];

    var subscribeCallbackName = function subscribeCallbackName(eventName, callbackName) {
      var boundCallback = _this[callbackName].bind(_this);
      _this.subscribe(eventName, boundCallback);

      _this.unsubscribeCallbacks.push(function () {
        // Bug: this.unsubscribe isn't exist!
        _this.base.unsubscribe(eventName, boundCallback);
      });
    };

    if (this.hideOnBlurDelay !== null && this.hideOnBlurDelay !== undefined) {
      // for hideOnBlurDelay, the panel should hide after blur event
      subscribeCallbackName("blur", "handleBlur");
      // and clear out hide timeout if focus again
      subscribeCallbackName("focus", "handleFocus");
    }
    // if the editor changes its content, we have to show or hide the panel
    subscribeCallbackName("editableKeyup", "handleKeyup");
  },
  detachEventHandlers: function detachEventHandlers() {
    if (this.hideOnBlurDelayId) {
      clearTimeout(this.hideOnBlurDelayId);
    }
    if (this.unsubscribeCallbacks) {
      this.unsubscribeCallbacks.forEach(function (boundCallback) {
        return boundCallback();
      });
      this.unsubscribeCallbacks = null;
    }
  },
  handleBlur: function handleBlur() {
    var _this2 = this;

    if (this.hideOnBlurDelay !== null && this.hideOnBlurDelay !== undefined) {
      this.hideOnBlurDelayId = setTimeout(function () {
        _this2.hidePanel(false);
      }, this.hideOnBlurDelay);
    }
  },
  handleFocus: function handleFocus() {
    if (this.hideOnBlurDelayId) {
      clearTimeout(this.hideOnBlurDelayId);
      this.hideOnBlurDelayId = null;
    }
  },
  handleKeyup: function handleKeyup(event) {
    var keyCode = _mediumEditor2.default.util.getKeyCode(event);
    var isSpace = keyCode === _mediumEditor2.default.util.keyCode.SPACE;
    this.getWordFromSelection(event.target, isSpace ? -1 : 0);

    if (!isSpace && this.activeTriggerList.indexOf(this.trigger) !== -1 && this.word.length > 1) {
      this.showPanel();
    } else {
      this.hidePanel(keyCode === LEFT_ARROW_KEYCODE);
      if (this.activeMentionAt && this.activeMentionAt.parentNode) {
        this.base.saveSelection();
        unwrapForTextNode(this.activeMentionAt, this.document);
        this.base.restoreSelection();
      }
    }
  },
  hidePanel: function hidePanel(isArrowTowardsLeft) {
    this.mentionPanel.classList.remove("medium-editor-mention-panel-active");
    var extraActivePanelClassName = this.extraActivePanelClassName || this.extraActiveClassName;

    if (extraActivePanelClassName) {
      this.mentionPanel.classList.remove(extraActivePanelClassName);
    }
    if (this.activeMentionAt) {
      this.activeMentionAt.classList.remove(this.activeTriggerClassName);
      if (this.extraActiveTriggerClassName) {
        this.activeMentionAt.classList.remove(this.extraActiveTriggerClassName);
      }
    }
    if (this.activeMentionAt) {
      // http://stackoverflow.com/a/27004526/1458162
      var _activeMentionAt = this.activeMentionAt,
          parentNode = _activeMentionAt.parentNode,
          previousSibling = _activeMentionAt.previousSibling,
          nextSibling = _activeMentionAt.nextSibling,
          firstChild = _activeMentionAt.firstChild;

      var siblingNode = isArrowTowardsLeft ? previousSibling : nextSibling;
      var textNode = void 0;
      if (!siblingNode) {
        textNode = this.document.createTextNode("");
        parentNode.appendChild(textNode);
      } else if (siblingNode.nodeType !== 3) {
        textNode = this.document.createTextNode("");
        parentNode.insertBefore(textNode, siblingNode);
      } else {
        textNode = siblingNode;
      }
      var lastEmptyWord = last(firstChild.textContent);
      var hasLastEmptyWord = lastEmptyWord.trim().length === 0;
      if (hasLastEmptyWord) {
        var textContent = firstChild.textContent;

        firstChild.textContent = textContent.substr(0, textContent.length - 1);
        textNode.textContent = "" + lastEmptyWord + textNode.textContent;
      } else {
        if (textNode.textContent.length === 0 && firstChild.textContent.length > 1) {
          textNode.textContent = "\xA0";
        }
      }
      if (isArrowTowardsLeft) {
        _mediumEditor2.default.selection.select(this.document, textNode, textNode.length);
      } else {
        _mediumEditor2.default.selection.select(this.document, textNode, Math.min(textNode.length, 1));
      }
      if (firstChild.textContent.length <= 1) {
        // LIKE core#execAction
        this.base.saveSelection();
        unwrapForTextNode(this.activeMentionAt, this.document);
        this.base.restoreSelection();
      }
      //
      this.activeMentionAt = null;
    }
  },
  getWordFromSelection: function getWordFromSelection(target, initialDiff) {
    var _MediumEditor$selecti = _mediumEditor2.default.selection.getSelectionRange(this.document),
        startContainer = _MediumEditor$selecti.startContainer,
        startOffset = _MediumEditor$selecti.startOffset,
        endContainer = _MediumEditor$selecti.endContainer;

    if (startContainer !== endContainer) {
      return;
    }
    var textContent = startContainer.textContent;


    function getWordPosition(position, diff) {
      var prevText = textContent[position - 1];
      if (prevText === null || prevText === undefined) {
        return position;
      } else if (prevText.trim().length === 0 || position <= 0 || textContent.length < position) {
        return position;
      } else {
        return getWordPosition(position + diff, diff);
      }
    }

    this.wordStart = getWordPosition(startOffset + initialDiff, -1);
    this.wordEnd = getWordPosition(startOffset + initialDiff, 1) - 1;
    this.word = textContent.slice(this.wordStart, this.wordEnd);
    this.trigger = this.word.slice(0, 1);
    this.triggerClassName = this.triggerClassNameMap[this.trigger];
    this.activeTriggerClassName = this.activeTriggerClassNameMap[this.trigger];
    //
    this.extraTriggerClassName = this.extraTriggerClassNameMap[this.trigger];
    this.extraActiveTriggerClassName = this.extraActiveTriggerClassNameMap[this.trigger];
  },
  showPanel: function showPanel() {
    if (!this.mentionPanel.classList.contains("medium-editor-mention-panel-active")) {
      this.activatePanel();
      this.wrapWordInMentionAt();
    }
    this.updatePanelContent();
    this.positionPanel();
  },
  activatePanel: function activatePanel() {
    this.mentionPanel.classList.add("medium-editor-mention-panel-active");
    if (this.extraActivePanelClassName || this.extraActiveClassName) {
      this.mentionPanel.classList.add(this.extraActivePanelClassName || this.extraActiveClassName);
    }
  },
  wrapWordInMentionAt: function wrapWordInMentionAt() {
    var selection = this.document.getSelection();
    if (!selection.rangeCount) {
      return;
    }
    // http://stackoverflow.com/a/6328906/1458162
    var range = selection.getRangeAt(0).cloneRange();
    if (range.startContainer.parentNode.classList.contains(this.triggerClassName)) {
      this.activeMentionAt = range.startContainer.parentNode;
    } else {
      var nextWordEnd = Math.min(this.wordEnd, range.startContainer.textContent.length);
      range.setStart(range.startContainer, this.wordStart);
      range.setEnd(range.startContainer, nextWordEnd);
      // Instead, insert our own version of it.
      // TODO: Not sure why, but using <span> tag doens't work here
      var element = this.document.createElement(this.tagName);
      element.classList.add(this.triggerClassName);
      if (this.extraTriggerClassName) {
        element.classList.add(this.extraTriggerClassName);
      }
      this.activeMentionAt = element;
      //
      range.surroundContents(element);
      selection.removeAllRanges();
      selection.addRange(range);
      //
      _mediumEditor2.default.selection.select(this.document, this.activeMentionAt.firstChild, this.word.length);
    }
    this.activeMentionAt.classList.add(this.activeTriggerClassName);
    if (this.extraActiveTriggerClassName) {
      this.activeMentionAt.classList.add(this.extraActiveTriggerClassName);
    }
  },
  positionPanel: function positionPanel() {
    var _activeMentionAt$getB = this.activeMentionAt.getBoundingClientRect(),
        top = _activeMentionAt$getB.top,
        bottom = _activeMentionAt$getB.bottom,
        left = _activeMentionAt$getB.left;

    var _window = this.window,
        pageXOffset = _window.pageXOffset,
        pageYOffset = _window.pageYOffset;

    var winHeight = this.document.documentElement.clientHeight || this.window.innerHeight;
    var horizontalPosition = pageXOffset + left;
    var verticalPosition = bottom > winHeight / 2 ? pageYOffset + top - this.mentionPanel.clientHeight : pageYOffset + bottom;

    this.mentionPanel.style.top = verticalPosition + "px";
    this.mentionPanel.style.left = horizontalPosition + "px";
  },
  updatePanelContent: function updatePanelContent() {
    this.renderPanelContent(this.mentionPanel, this.word, this.handleSelectMention.bind(this));
  },
  handleSelectMention: function handleSelectMention(selectedText, details) {
    if (selectedText) {
      var textNode = this.activeMentionAt.firstChild;
      if (details["name"] && details["name"].length > 0) {
        this.activeMentionAt.setAttribute("data-name", details["name"]);
      }
      if (details["first-name"] && details["first-name"].length > 0) {
        this.activeMentionAt.setAttribute("data-first-name", details["first-name"]);
      }
      if (details["last-name"] && details["last-name"].length > 0) {
        this.activeMentionAt.setAttribute("data-last-name", details["last-name"]);
      }
      if (details["slack-username"] && details["slack-username"].length > 0) {
        this.activeMentionAt.setAttribute("data-slack-username", details["slack-username"]);
      } else if (details["slack-usernames"] && details["slack-usernames"].length > 0) {
        this.activeMentionAt.setAttribute("data-slack-username", details["slack-usernames"][0]);
      }
      if (details["user-id"] && details["user-id"].length > 0) {
        this.activeMentionAt.setAttribute("data-user-id", details["user-id"]);
      }
      if (details["email"] && details["email"].length > 0) {
        this.activeMentionAt.setAttribute("data-email", details["email"]);
      }
      if (details["avatar-url"] && details["avatar-url"].length > 0) {
        this.activeMentionAt.setAttribute("data-avatar-url", details["avatar-url"]);
      }
      this.activeMentionAt.setAttribute("data-found", "true");
      textNode.textContent = selectedText;
      _mediumEditor2.default.selection.select(this.document, textNode, selectedText.length);
      //
      // If one of our contenteditables currently has focus, we should
      // attempt to trigger the 'editableInput' event
      var target = this.base.getFocusedElement();
      if (target) {
        this.base.events.updateInput(target, {
          target: target,
          currentTarget: target
        });
      }
      //
      this.hidePanel(false);
    } else {
      this.hidePanel(false);
    }
  }
});

exports.default = TCMention;