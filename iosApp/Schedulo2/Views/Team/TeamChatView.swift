import SwiftUI

struct TeamChatView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var messageText = ""
    @State private var isAnnouncement = false

    private var isOwner: Bool {
        teamViewModel.currentTeam?.ownerId == teamViewModel.currentUserId
    }

    private var pinnedMessages: [TeamMessageInfo] {
        teamViewModel.teamMessages.filter { $0.isPinned }
    }

    private var sortedMessages: [TeamMessageInfo] {
        teamViewModel.teamMessages.sorted { $0.createdAt > $1.createdAt }
    }

    private let timeFmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM dd, h:mm a"
        return f
    }()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if !pinnedMessages.isEmpty {
                    pinnedSection
                }
                messageList
                Divider()
                if teamViewModel.isManager {
                    announceToggle
                }
                inputBar
            }
            .navigationTitle("Team Chat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var pinnedSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Image(systemName: "pin.fill")
                    .font(.system(size: 11))
                    .foregroundColor(.accentOrange)
                Text("Pinned")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.accentOrange)
            }
            ForEach(pinnedMessages.prefix(3)) { msg in
                Text("\(msg.senderName.isEmpty ? "Unknown" : msg.senderName): \(msg.text)")
                    .font(.system(size: 12))
                    .lineLimit(1)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.accentOrange.opacity(0.1))
        )
        .padding(.horizontal, 12)
        .padding(.vertical, 4)
    }

    private var messageList: some View {
        ScrollView {
            ScrollViewReader { proxy in
                LazyVStack(spacing: 6) {
                    ForEach(sortedMessages.reversed()) { message in
                        chatBubble(message)
                            .id(message.id)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }
        }
        .frame(maxHeight: .infinity)
    }

    private func chatBubble(_ message: TeamMessageInfo) -> some View {
        let isMe = message.senderId == teamViewModel.currentUserId
        let bgColor: Color = {
            if message.isAnnouncement { return .accentOrange.opacity(0.12) }
            if isMe { return .primaryGreen.opacity(0.12) }
            return Color(UIColor.secondarySystemBackground)
        }()

        return VStack(alignment: isMe ? .trailing : .leading, spacing: 2) {
            HStack {
                if isMe { Spacer(minLength: 60) }
                VStack(alignment: .leading, spacing: 4) {
                    if message.isAnnouncement {
                        HStack(spacing: 4) {
                            Image(systemName: "megaphone.fill")
                                .font(.system(size: 11))
                                .foregroundColor(.accentOrange)
                            Text("Announcement")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.accentOrange)
                        }
                    }
                    if !isMe {
                        Text(message.senderName.isEmpty ? "Unknown" : message.senderName)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)
                    }
                    Text(message.text)
                        .font(.system(size: 14))
                    HStack(spacing: 4) {
                        Text(timeFmt.string(from: message.createdDate))
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                        if message.isPinned {
                            Image(systemName: "pin.fill")
                                .font(.system(size: 9))
                                .foregroundColor(.accentOrange)
                        }
                    }
                }
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(bgColor)
                )
                if !isMe { Spacer(minLength: 60) }
            }

            if isMe || isOwner {
                HStack(spacing: 12) {
                    if isOwner {
                        Button(action: { teamViewModel.togglePin(messageId: message.id) }) {
                            Image(systemName: message.isPinned ? "pin.slash" : "pin")
                                .font(.system(size: 12))
                                .foregroundColor(message.isPinned ? .accentOrange : .secondary)
                        }
                    }
                    Button(action: { teamViewModel.deleteMessage(messageId: message.id) }) {
                        Image(systemName: "trash")
                            .font(.system(size: 12))
                            .foregroundColor(.red.opacity(0.6))
                    }
                }
                .padding(.horizontal, 4)
            }
        }
        .frame(maxWidth: .infinity, alignment: isMe ? .trailing : .leading)
    }

    private var announceToggle: some View {
        HStack {
            Button(action: { isAnnouncement.toggle() }) {
                HStack(spacing: 4) {
                    Image(systemName: isAnnouncement ? "megaphone.fill" : "megaphone")
                        .font(.system(size: 12))
                    Text("Announce")
                        .font(.system(size: 12, weight: .medium))
                }
                .foregroundColor(isAnnouncement ? .white : .accentOrange)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(
                    RoundedRectangle(cornerRadius: 14)
                        .fill(isAnnouncement ? Color.accentOrange : Color.accentOrange.opacity(0.12))
                )
            }
            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 4)
    }

    private var inputBar: some View {
        HStack(spacing: 8) {
            TextField("Type a message...", text: $messageText, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(1...3)

            Button(action: {
                guard !messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                teamViewModel.sendMessage(text: messageText, isAnnouncement: isAnnouncement)
                messageText = ""
                isAnnouncement = false
            }) {
                Image(systemName: "paperplane.fill")
                    .font(.system(size: 18))
                    .foregroundColor(messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? .secondary : .primaryGreen)
            }
            .disabled(messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }
}
