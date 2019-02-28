function selectElementText(el) {
	if (window.getSelection && document.createRange) {
		var sel = window.getSelection();
		range = document.createRange();
		range.selectNodeContents(el);
		sel.removeAllRanges();
		sel.addRange(range);
	} else if (document.body.createTextRange) {
		var range = document.body.createTextRange();
		range.moveToElementText(el);
		range.select();
		alert('py');
	}
}

function deSelectAll() {
	if (window.getSelection) {
		window.getSelection().removeAllRanges();
	} else if (document.selection) {
		document.selection.empty();
	}
}

function isIE() {
	if (navigator.appName == 'Microsoft Internet Explorer' ||  !!(navigator.userAgent.match(/Trident/) || navigator.userAgent.match(/rv:11/)) || (typeof $.browser !== "undefined" && $.browser.msie == 1))
	{
  		return true;
	}
	return false;
}

function copyElement(elementId) {
	if (isIE()) {
		alert('You seem to be using Internet Explorer. The content may not be copied with formatting. Please consider using a different browser.');
	}
	var el = document.getElementById(elementId);
	selectElementText(el);
	try {
		var ok = document.execCommand('copy');
		if (ok)
			alert("Content copied to clipboard.");

		else
			alert("Dammit, this didn't work.");
	}
	catch (err) {
		alert("Unsupported browser");
	}
	deSelectAll();
}