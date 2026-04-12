const resetBtn = document.getElementById('resetBtn');
if (resetBtn) {
    console.log("Found resetBtn, clicking it!");
    
    // confirm 창을 무조건 true로 강제 (자동화 테스트를 위해)
    const originalConfirm = window.confirm;
    window.confirm = function() { return true; };
    
    resetBtn.click();
    
    // Restore confirm
    window.confirm = originalConfirm;
} else {
    console.log("resetBtn not found on page");
}
