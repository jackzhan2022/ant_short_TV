const config = require("./config");
App({
	globalData: { apiBase: "", appid: "", top: 24 },
	onLaunch() {
		const ext = wx.getExtConfigSync ? wx.getExtConfigSync() : {};
		const account = wx.getAccountInfoSync();
		const window = wx.getWindowInfo();
		this.globalData = {
			apiBase: (ext.apiBase || config.apiBase).replace(/\/$/, ""),
			appid: account.miniProgram.appId,
			top: window.statusBarHeight || 24,
		};
	},
});
