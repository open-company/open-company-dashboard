
function OCStaticMailchimpApiSubmit(e, form, success, fail){
  e.preventDefault();
  $.ajax({
    type: "POST",
    url: form.action,
    contentType: "text/plain",
    data: $("input." + form.id + "-input").val(),
    success: function(a, b){
      if (typeof success === "function") {
        success();
      }
    },
    error: function(a, b){
      if (typeof fail === "function") {
        fail();
      }
    }
  });
}

function OCStaticGetCookie(name) {
  // Give a cookie name return its value
  var value = "; " + document.cookie;
  var parts = value.split("; " + name + "=");
  if (parts.length == 2)
    return decodeURI(parts.pop().split(";").shift());
}

function OCStaticCookieName(name){
  var h = window.location.hostname.split(".")[0];
  var prefix = "";
  switch(h) {
    case "localhost":
      prefix = "localhost-";
      break;
    case "staging":
      prefix = "staging-";
      break;
  }
  return prefix + name;
}

function OCStaticGetDecodedJWT(jwt) {
  if (jwt && typeof jwt_decode === "function") {
    try {
      return jwt_decode(jwt);
    } catch(error) {
      return null;
    }
  }
  return null;
}

function OCStaticGetYourBoardsUrl (decoded_jwt) {
  var your_board_url = "/login";
  if ( decoded_jwt ) {
    var user_id,
        org_slug,
        board_slug;
    if (decoded_jwt) {
      user_id = decoded_jwt["user-id"];
      if ( user_id ) {
        org_slug = OCStaticGetCookie(OCStaticCookieName("last-org-" + user_id));
        if ( org_slug ) {
          board_slug = "all-posts";
          // Replace all-posts above withe the following to go back to the last visited board
          // OCStaticGetCookie(OCStaticCookieName("last-board-" + user_id + "-" + org_slug));
          if ( board_slug ){
            your_board_url = "/" + org_slug + "/" + board_slug;
          } else {
            your_board_url = "/" + org_slug;
          }
        }
      }
    }
  }
  return your_board_url;
}


document.addEventListener("DOMContentLoaded", function(_) {
  // Get the jwt cookie to know if the user is logged in
  var jwt = OCStaticGetCookie(OCStaticCookieName("jwt"));
  if (jwt) {
    $("#site-header-login-item").hide();
    // Move the red guy up
    $("div.home-page").addClass("no-get-started-button");
    $("div.main.slack").addClass("no-get-started-button");
    // Remove the signup with slack buttons
    $("div.sigin-with-slack-container").css({"display": "none"});
    // Remove the get started centered button if the user is signed in
    $("#get-started-centred-bt").css({"display": "none"});
    // Remove the get started bottom button if the user is signed in
    $("div.about-bottom-get-started").css({"display": "none"});
    // Hide the try it box at the bottom of the homepage
    $("section.third-section").css({"display": "none"});
    // Remove login button from the site mobile menu
    $("button#site-mobile-menu-login").css({"display": "none"});
    // Change Get started button to Your boards on site mobile menu
    var siteMobileMenuGetStarted = $("button#site-mobile-menu-getstarted");
    siteMobileMenuGetStarted.text( "Your Boards" );
    siteMobileMenuGetStarted.addClass("your-boards");
    // Top right corner became Your Boards
    var signupButton = $("#site-header-signup-item");
    signupButton.addClass("your-boards");

    var decoded_jwt = OCStaticGetDecodedJWT(jwt),
        your_board_url = OCStaticGetYourBoardsUrl(decoded_jwt);
    signupButton.attr("href", your_board_url);
    signupButton.html("<span class=\"go-to-digest\">Go to digest</span>");

    var mobileSignupButton = $("#site-header-mobile-signup-item");
    mobileSignupButton.removeClass("start");
    mobileSignupButton.attr("href", your_board_url);
    mobileSignupButton.html("<span class=\"go-to-digest\">Go to digest</span>");
    // Hide get started and login buttons in the footer
    $("div.footer-small-links.static").hide();
    // Set the action of the site mobile menu's Get started button
    siteMobileMenuGetStarted.attr("onClick", "window.location = \"" + your_board_url + "\"");
    // If in 404 page show error message for logged in users
    $("div.error-page.not-found-page p.not-logged-in").hide();

  }else{ // No logged in user
    // Remove get started button missing classes
    $("div.home-page").removeClass("no-get-started-button");
    $("div.main.slack").removeClass("no-get-started-button");
    // link all get started button to signup with Slack
    $(".get-started-button").attr("onClick", "window.location = \"/sign-up\"");
    $("button.signin-with-slack").attr("onClick", "window.location = \"/sign-up\"");
    // Top right corner signup button
    $("#site-header-signup-item").attr("href", "/sign-up");
    $("#site-header-mobile-signup-item").attr("href", "/sign-up");
    // Top right corner login button
    $("#site-header-login-item").attr("href", "/login");
    // Mobile menu login button
    $("button#site-mobile-menu-login").attr("onClick", "window.location = \"/login\"");
    // If in 404 page show error message for not logged in users
    $("div.error-page.not-found-page p.logged-in").show();
  }

});

function OCStaticGetParameterByName(name, url) {
  if (!url)
    url = window.location.href;
  name = name.replace(/[\[\]]/g, "\\$&");
  var regex = new RegExp("[?&]" + name + "(=([^&#]*)|&|#|$)"),
      results = regex.exec(url);
  if (!results)
    return null;
  if (!results[2])
    return '';
  return decodeURIComponent(results[2].replace(/\+/g, " "));
}

function isSafari(){
  var ua = navigator.userAgent.toLowerCase(); 
  if (ua.indexOf('safari') > -1) { 
    if (ua.indexOf('chrome') > -1) {
      return false;
    } else {
      return true;
    }
  }
  return false;
}

function isFireFox(){
  var ua = navigator.userAgent.toLowerCase();
  if (ua.match(/Firefox/) && !ua.match(/Seamonkey/)) {
      return true;
  } else {
      return false;
  }
}

function isEdge(){
  if (navigator.appName == 'Microsoft Internet Explorer' ||
      !!(navigator.userAgent.match(/Trident/) || navigator.userAgent.match(/rv:11/) || navigator.userAgent.match(/Edge\/\d+/)))
  {
    return true;
  }else{
    return false;
  }
}

function isIE(){
  if (navigator.appName == 'Microsoft Internet Explorer' ||
      !!(navigator.userAgent.match(/Trident/) ||
         navigator.userAgent.match(/rv:11/)))
  {
    return true;
  }else{
    return false;
  }
}

function OCStaticSiteMobileMenuToggle(){
  var menuClass = "mobile-menu-expanded";
  var body = document.body;
  if (body.classList.contains(menuClass)) {
    body.querySelector("div.site-mobile-menu").classList.add("hidden");
    body.classList.remove(menuClass);
  } else {
    body.querySelector("div.site-mobile-menu").classList.remove("hidden");
    body.classList.add(menuClass);
  }
}


function OCStaticStartFixFixedPositioning(sel) {
  // Let's assume the fixed top navbar has id="navbar"
  // Cache the fixed element
  var $navbar = $(sel);

  var fixFixedPosition = function() {
    $navbar.css({
      position: 'absolute',
      top: document.body.scrollTop + 'px'
    });
  };
  var resetFixedPosition = function() {
    $navbar.css({
      position: 'fixed',
      top: ''
    });
    $(document).off('scroll', updateScrollTop);
  };
  var updateScrollTop = function() {
    $navbar.css('top', document.body.scrollTop + 'px');
  };

  $('input, textarea, [contenteditable=true]').on({
    focus: function() {
      // NOTE: The delay is required.
      setTimeout(fixFixedPosition, 100);
      // Keep the fixed element absolutely positioned at the top
      // when the keyboard is visible
      $(document).scroll(updateScrollTop);
    },
    blur: resetFixedPosition
  });
}