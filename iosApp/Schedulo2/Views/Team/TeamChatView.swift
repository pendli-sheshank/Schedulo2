import SwiftUI
import PhotosUI

struct TeamChatView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var messageText = ""
    @State private var isAnnouncement = false
    @State private var selectedPhoto: PhotosPickerItem?

    private var isOwner: Bool {
        teamViewModel.currentTeam?.ownerId == teamViewModel.currentUserId
    }

    private var pinnedMessages: [TeamMessageInfo] {
        teamViewModel.teamMessages.filter { $0.isPinned }
    }

    private var sortedMessages: [TeamMessageInfo] {
        teamViewModel.teamMessages.sorted { $0.createdAt > $1.createdAt }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if !pinnedMessages.isEmpty {
                    pinnedSection
                }
                messageList
                Divider()
                if teamViewModel.isUploadingImage {
                    ProgressView()
                        .tint(.primaryGreen)
                        .padding(.vertical, 4)
                }
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
            .onChange(of: selectedPhoto) { newItem in
                guard let item = newItem else { return }
                item.loadTransferable(type: Data.self) { result in
                    if case .success(let data) = result, let data = data, let image = UIImage(data: data) {
                        DispatchQueue.main.async {
                            teamViewModel.sendImage(image)
                        }
                    }
                }
                selectedPhoto = nil
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
                let preview: String = msg.imageUrl.isEmpty ? msg.text : "Sent a photo"
                Text("\(msg.senderName.isEmpty ? "Unknown" : msg.senderName): \(preview)")
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
                        ChatBubbleView(
                            message: message,
                            isMe: message.senderId == teamViewModel.currentUserId,
                            isOwner: isOwner
                        )
                        .id(message.id)
                        .onAppear {
                            if !message.imageUrl.isEmpty {
                                teamViewModel.markMessageSeen(messageId: message.id)
                            }
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }
        }
        .frame(maxHeight: .infinity)
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
            PhotosPicker(selection: $selectedPhoto, matching: .images) {
                Image(systemName: "photo.fill")
                    .font(.system(size: 20))
                    .foregroundColor(.primaryGreen)
            }
            .disabled(teamViewModel.isUploadingImage)

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

private extension DateFormatter {
    static let chatTime: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM dd, h:mm a"
        return f
    }()
}

private struct ChatBubbleView: View {
    let message: TeamMessageInfo
    let isMe: Bool
    let isOwner: Bool
    @EnvironmentObject var teamViewModel: TeamViewModel

    private var bgColor: Color {
        if message.isAnnouncement { return .accentOrange.opacity(0.12) }
        if isMe { return .primaryGreen.opacity(0.12) }
        return Color(UIColor.secondarySystemBackground)
    }

    var body: some View {
        VStack(alignment: isMe ? .trailing : .leading, spacing: 2) {
            HStack {
                if isMe { Spacer(minLength: 60) }
                VStack(alignment: .leading, spacing: 4) {
                    if message.isAnnouncement {
                        announcementBadge
                    }
                    if !isMe {
                        let senderName: String = message.senderName.isEmpty ? "Unknown" : message.senderName
                        Text(senderName)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)
                    }
                    if !message.imageUrl.isEmpty {
                        AsyncImage(url: URL(string: message.imageUrl)) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(maxWidth: 220, maxHeight: 220)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            case .failure:
                                Text("Image expired")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            default:
                                ProgressView()
                                    .frame(width: 100, height: 100)
                            }
                        }
                    }
                    if !message.text.isEmpty {
                        Text(message.text)
                            .font(.system(size: 14))
                    }
                    timestampRow
                }
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(bgColor)
                )
                if !isMe { Spacer(minLength: 60) }
            }

            if isMe || isOwner {
                actionButtons
            }
        }
        .frame(maxWidth: .infinity, alignment: isMe ? .trailing : .leading)
    }

    private var announcementBadge: some View {
        HStack(spacing: 4) {
            Image(systemName: "megaphone.fill")
                .font(.system(size: 11))
                .foregroundColor(.accentOrange)
            Text("Announcement")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.accentOrange)
        }
    }

    private var timestampRow: some View {
        HStack(spacing: 4) {
            Text(DateFormatter.chatTime.string(from: message.createdDate))
                .font(.system(size: 10))
                .foregroundColor(.secondary)
            if message.isPinned {
                Image(systemName: "pin.fill")
                    .font(.system(size: 9))
                    .foregroundColor(.accentOrange)
            }
            if !message.seenBy.isEmpty && !message.imageUrl.isEmpty {
                Text("Seen by \(message.seenBy.count)")
                    .font(.system(size: 9))
                    .foregroundColor(.secondary)
            }
        }
    }

    private var actionButtons: some View {
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
