module github.com/yttydcs/myflowhub-android/hubmobile

go 1.25.0

require github.com/yttydcs/myflowhub-server v0.0.13

require (
	github.com/yttydcs/myflowhub-core v0.4.9
	github.com/yttydcs/myflowhub-proto v0.1.5
	github.com/yttydcs/myflowhub-sdk v0.1.13
	golang.org/x/mobile v0.0.0-20260217195705-b56b3793a9c4
)

require (
	github.com/godbus/dbus/v5 v5.2.2 // indirect
	github.com/jackc/pgpassfile v1.0.0 // indirect
	github.com/jackc/pgservicefile v0.0.0-20240606120523-5a60cdf6a761 // indirect
	github.com/jackc/pgx/v5 v5.9.1 // indirect
	github.com/quic-go/quic-go v0.59.0 // indirect
	github.com/yttydcs/myflowhub-subproto/auth v0.1.5 // indirect
	github.com/yttydcs/myflowhub-subproto/broker v0.1.1 // indirect
	github.com/yttydcs/myflowhub-subproto/exec v0.1.2 // indirect
	github.com/yttydcs/myflowhub-subproto/file v0.1.4 // indirect
	github.com/yttydcs/myflowhub-subproto/flow v0.1.2 // indirect
	github.com/yttydcs/myflowhub-subproto/forward v0.1.1 // indirect
	github.com/yttydcs/myflowhub-subproto/management v0.1.4 // indirect
	github.com/yttydcs/myflowhub-subproto/stream v0.1.0 // indirect
	github.com/yttydcs/myflowhub-subproto/topicbus v0.1.2 // indirect
	github.com/yttydcs/myflowhub-subproto/varstore v0.1.4 // indirect
	golang.org/x/crypto v0.48.0 // indirect
	golang.org/x/mod v0.33.0 // indirect
	golang.org/x/net v0.50.0 // indirect
	golang.org/x/sync v0.19.0 // indirect
	golang.org/x/sys v0.42.0 // indirect
	golang.org/x/text v0.34.0 // indirect
	golang.org/x/tools v0.42.0 // indirect
)

// 开发态：在本 meta-workspace 下使用同一 workflow 的 Server worktree。
// 发布态：可移除此 replace，改为依赖 myflowhub-server 的发布版本。
replace github.com/yttydcs/myflowhub-server => ../../MyFlowHub-Server

replace github.com/yttydcs/myflowhub-sdk => ../../MyFlowHub-SDK

replace github.com/yttydcs/myflowhub-proto => ../../MyFlowHub-Proto
