import Foundation
import Combine
import UIKit
import UserNotifications
import FirebaseAuth
import FirebaseFirestore
import FirebaseStorage

struct TeamInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var name: String = ""
    var ownerId: String = ""
    var inviteCode: String = ""
    var createdAt: Int64 = 0
    var memberCount: Int = 0
    var weeklyCycleStartDay: String = "Monday"
}

struct TeamMemberInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var userId: String = ""
    var role: String = "member"
    var joinedAt: Int64 = 0
    var displayName: String = ""
    var email: String = ""
}

struct ShiftTaskInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var title: String = ""
    var isCompleted: Bool = false
}

struct TeamShiftInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var assignedTo: String = ""
    var assignedBy: String = ""
    var company: String = ""
    var role: String = ""
    var startTime: Int64 = 0
    var endTime: Int64 = 0
    var hourlyRate: Double = 0.0
    var notes: String = ""
    var status: String = "assigned"
    var tasks: [ShiftTaskInfo] = []

    var durationHours: Double {
        guard endTime > startTime else { return 0.0 }
        return Double(endTime - startTime) / 3_600_000.0
    }

    var startDate: Date {
        Date(timeIntervalSince1970: Double(startTime) / 1000.0)
    }

    var endDate: Date {
        Date(timeIntervalSince1970: Double(endTime) / 1000.0)
    }
}

struct TeamMessageInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var senderId: String = ""
    var senderName: String = ""
    var text: String = ""
    var isAnnouncement: Bool = false
    var isPinned: Bool = false
    var imageUrl: String = ""
    var seenBy: [String] = []
    var createdAt: Int64 = 0

    var createdDate: Date {
        Date(timeIntervalSince1970: Double(createdAt) / 1000.0)
    }
}

struct SwapRequestInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var requesterId: String = ""
    var requesterName: String = ""
    var requesterShiftId: String = ""
    var targetMemberId: String = ""
    var targetMemberName: String = ""
    var targetShiftId: String = ""
    var status: String = "pending"
    var createdAt: Int64 = 0
    var resolvedAt: Int64 = 0
    var resolvedBy: String = ""
}

struct MemberJobInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var title: String = ""
    var defaultHourlyRate: Double = 15.0
    var isGigWork: Bool = false
}

final class TeamViewModel: ObservableObject {
    @Published var teams: [TeamInfo] = []
    @Published var currentTeam: TeamInfo?
    @Published var members: [TeamMemberInfo] = []
    @Published var teamShifts: [TeamShiftInfo] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var userRole: String = "member"
    @Published var memberJobs: [MemberJobInfo] = []
    @Published var teamMessages: [TeamMessageInfo] = []
    @Published var swapRequests: [SwapRequestInfo] = []

    @Published var isUploadingImage = false

    private let db = Firestore.firestore(database: "schedulo2")
    private let storageRef = Storage.storage().reference()
    private var teamsListener: ListenerRegistration?
    private var membersListener: ListenerRegistration?
    private var shiftsListener: ListenerRegistration?
    private var memberJobsListener: ListenerRegistration?
    private var messagesListener: ListenerRegistration?
    private var swapRequestsListener: ListenerRegistration?

    var currentUserId: String? { Auth.auth().currentUser?.uid }
    var currentUserEmail: String? { Auth.auth().currentUser?.email }

    var isManager: Bool { userRole == "manager" }

    func fetchMemberJobs(userId: String) {
        memberJobsListener?.remove()
        memberJobsListener = db.collection("jobs")
            .whereField("userId", isEqualTo: userId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let documents = snapshot?.documents else {
                    self?.memberJobs = []
                    return
                }
                self?.memberJobs = documents.compactMap { doc in
                    let data = doc.data()
                    return MemberJobInfo(
                        id: doc.documentID,
                        title: data["title"] as? String ?? "",
                        defaultHourlyRate: data["defaultHourlyRate"] as? Double ?? 15.0,
                        isGigWork: data["isGigWork"] as? Bool ?? false
                    )
                }
            }
    }

    func loadTeams() {
        guard let uid = currentUserId else { return }
        teamsListener?.remove()

        teamsListener = db.collection("team_members")
            .whereField("userId", isEqualTo: uid)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let docs = snapshot?.documents, error == nil else { return }
                let teamIds = docs.compactMap { $0.data()["teamId"] as? String }
                guard !teamIds.isEmpty else {
                    self?.teams = []
                    return
                }
                self?.fetchTeams(ids: teamIds)
            }
    }

    private func fetchTeams(ids: [String]) {
        let group = DispatchGroup()
        var fetched: [TeamInfo] = []

        for id in ids {
            group.enter()
            db.collection("teams").document(id).getDocument { snapshot, _ in
                defer { group.leave() }
                guard let data = snapshot?.data() else { return }
                let team = TeamInfo(
                    id: snapshot?.documentID ?? id,
                    name: data["name"] as? String ?? "",
                    ownerId: data["ownerId"] as? String ?? "",
                    inviteCode: data["inviteCode"] as? String ?? "",
                    createdAt: data["createdAt"] as? Int64 ?? 0,
                    memberCount: data["memberCount"] as? Int ?? 0,
                    weeklyCycleStartDay: data["weeklyCycleStartDay"] as? String ?? "Monday"
                )
                fetched.append(team)
            }
        }

        group.notify(queue: .main) { [weak self] in
            self?.teams = fetched.sorted { $0.name < $1.name }
            if self?.currentTeam == nil, let first = fetched.first {
                self?.selectTeam(first)
            }
        }
    }

    func selectTeam(_ team: TeamInfo) {
        currentTeam = team
        loadMembers(teamId: team.id)
        loadTeamShifts(teamId: team.id)
        loadTeamMessages(teamId: team.id)
        loadSwapRequests(teamId: team.id)
        updateUserRole(teamId: team.id)
    }

    private func updateUserRole(teamId: String) {
        guard let uid = currentUserId else { return }
        db.collection("team_members")
            .whereField("teamId", isEqualTo: teamId)
            .whereField("userId", isEqualTo: uid)
            .getDocuments { [weak self] snapshot, _ in
                if let doc = snapshot?.documents.first {
                    self?.userRole = doc.data()["role"] as? String ?? "member"
                }
            }
    }

    func loadMembers(teamId: String) {
        membersListener?.remove()
        membersListener = db.collection("team_members")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                self?.members = docs.map { doc in
                    let data = doc.data()
                    return TeamMemberInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        userId: data["userId"] as? String ?? "",
                        role: data["role"] as? String ?? "member",
                        joinedAt: data["joinedAt"] as? Int64 ?? 0,
                        displayName: data["displayName"] as? String ?? "",
                        email: data["email"] as? String ?? ""
                    )
                }.sorted { $0.displayName < $1.displayName }
            }
    }

    func loadTeamShifts(teamId: String) {
        shiftsListener?.remove()
        shiftsListener = db.collection("team_shifts")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                self?.teamShifts = docs.map { doc in
                    let data = doc.data()
                    let tasksRaw = data["tasks"] as? [[String: Any]] ?? []
                    let parsedTasks = tasksRaw.map { t in
                        ShiftTaskInfo(
                            id: t["id"] as? String ?? UUID().uuidString,
                            title: t["title"] as? String ?? "",
                            isCompleted: t["isCompleted"] as? Bool ?? false
                        )
                    }
                    return TeamShiftInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        assignedTo: data["assignedTo"] as? String ?? "",
                        assignedBy: data["assignedBy"] as? String ?? "",
                        company: data["company"] as? String ?? "",
                        role: data["role"] as? String ?? "",
                        startTime: (data["startTime"] as? NSNumber)?.int64Value ?? 0,
                        endTime: (data["endTime"] as? NSNumber)?.int64Value ?? 0,
                        hourlyRate: data["hourlyRate"] as? Double ?? 0.0,
                        notes: data["notes"] as? String ?? "",
                        status: data["status"] as? String ?? "assigned",
                        tasks: parsedTasks
                    )
                }.sorted { $0.startTime > $1.startTime }
            }
    }

    // MARK: - Team CRUD

    func createTeam(name: String) {
        guard let uid = currentUserId else { return }
        isLoading = true
        let teamId = UUID().uuidString
        let inviteCode = generateInviteCode()

        let teamData: [String: Any] = [
            "name": name,
            "ownerId": uid,
            "inviteCode": inviteCode,
            "createdAt": Int64(Date().timeIntervalSince1970 * 1000),
            "memberCount": 1,
            "weeklyCycleStartDay": "Monday"
        ]

        let memberData: [String: Any] = [
            "teamId": teamId,
            "userId": uid,
            "role": "manager",
            "joinedAt": Int64(Date().timeIntervalSince1970 * 1000),
            "displayName": currentUserEmail ?? "",
            "email": currentUserEmail ?? ""
        ]

        let batch = db.batch()
        batch.setData(teamData, forDocument: db.collection("teams").document(teamId))
        batch.setData(memberData, forDocument: db.collection("team_members").document(UUID().uuidString))

        batch.commit { [weak self] error in
            DispatchQueue.main.async {
                self?.isLoading = false
                if let error = error {
                    self?.errorMessage = error.localizedDescription
                } else {
                    self?.loadTeams()
                }
            }
        }
    }

    func joinTeam(inviteCode: String) {
        guard let uid = currentUserId else { return }
        isLoading = true

        db.collection("teams")
            .whereField("inviteCode", isEqualTo: inviteCode.uppercased())
            .getDocuments { [weak self] snapshot, error in
                guard let doc = snapshot?.documents.first else {
                    DispatchQueue.main.async {
                        self?.isLoading = false
                        self?.errorMessage = "Invalid invite code"
                    }
                    return
                }

                let teamId = doc.documentID

                self?.db.collection("team_members")
                    .whereField("teamId", isEqualTo: teamId)
                    .whereField("userId", isEqualTo: uid)
                    .getDocuments { existingSnapshot, _ in
                        if let existingDocs = existingSnapshot?.documents, !existingDocs.isEmpty {
                            DispatchQueue.main.async {
                                self?.isLoading = false
                                self?.errorMessage = "You are already a member of this team."
                            }
                            return
                        }

                        let memberData: [String: Any] = [
                            "teamId": teamId,
                            "userId": uid,
                            "role": "member",
                            "joinedAt": Int64(Date().timeIntervalSince1970 * 1000),
                            "displayName": self?.currentUserEmail ?? "",
                            "email": self?.currentUserEmail ?? ""
                        ]

                        let batch = self?.db.batch()
                        batch?.setData(memberData, forDocument: self?.db.collection("team_members").document(UUID().uuidString) ?? self!.db.collection("team_members").document())
                        batch?.updateData(["memberCount": FieldValue.increment(Int64(1))], forDocument: self?.db.collection("teams").document(teamId) ?? self!.db.collection("teams").document(teamId))

                        batch?.commit { error in
                            DispatchQueue.main.async {
                                self?.isLoading = false
                                if let error = error {
                                    self?.errorMessage = error.localizedDescription
                                } else {
                                    self?.loadTeams()
                                }
                            }
                        }
                    }
            }
    }

    func assignShift(to memberId: String, company: String, role: String, startTime: Int64, endTime: Int64, hourlyRate: Double, notes: String, tasks: [ShiftTaskInfo] = []) {
        guard let teamId = currentTeam?.id, let uid = currentUserId else { return }
        let shiftId = UUID().uuidString

        let tasksData = tasks.map { t -> [String: Any] in
            ["id": t.id, "title": t.title, "isCompleted": t.isCompleted]
        }

        let data: [String: Any] = [
            "teamId": teamId,
            "assignedTo": memberId,
            "assignedBy": uid,
            "company": company,
            "role": role,
            "startTime": startTime,
            "endTime": endTime,
            "hourlyRate": hourlyRate,
            "notes": notes,
            "status": "assigned",
            "tasks": tasksData
        ]

        db.collection("team_shifts").document(shiftId).setData(data) { [weak self] error in
            if error == nil {
                let teamShift = TeamShiftInfo(
                    id: shiftId,
                    teamId: teamId,
                    assignedTo: memberId,
                    assignedBy: uid,
                    company: company,
                    role: role,
                    startTime: startTime,
                    endTime: endTime,
                    hourlyRate: hourlyRate,
                    notes: notes,
                    status: "assigned",
                    tasks: tasks
                )
                self?.createPersonalShiftFromTeam(teamShift, targetUserId: memberId)
            } else {
                DispatchQueue.main.async {
                    self?.errorMessage = error?.localizedDescription
                }
            }
        }
    }

    func toggleTaskCompletion(shiftId: String, taskId: String) {
        let ref = db.collection("team_shifts").document(shiftId)
        ref.getDocument { [weak self] snapshot, _ in
            guard let data = snapshot?.data(),
                  var tasks = data["tasks"] as? [[String: Any]] else { return }
            if let idx = tasks.firstIndex(where: { $0["id"] as? String == taskId }) {
                let current = tasks[idx]["isCompleted"] as? Bool ?? false
                tasks[idx]["isCompleted"] = !current
                if let localIdx = self?.teamShifts.firstIndex(where: { $0.id == shiftId }),
                   let taskIdx = self?.teamShifts[localIdx].tasks.firstIndex(where: { $0.id == taskId }) {
                    DispatchQueue.main.async {
                        self?.teamShifts[localIdx].tasks[taskIdx].isCompleted = !current
                    }
                }
                ref.updateData(["tasks": tasks]) { error in
                    if let error = error {
                        DispatchQueue.main.async {
                            self?.errorMessage = error.localizedDescription
                            if let localIdx = self?.teamShifts.firstIndex(where: { $0.id == shiftId }),
                               let taskIdx = self?.teamShifts[localIdx].tasks.firstIndex(where: { $0.id == taskId }) {
                                self?.teamShifts[localIdx].tasks[taskIdx].isCompleted = current
                            }
                        }
                    }
                }
            }
        }
    }

    private func createPersonalShiftFromTeam(_ teamShift: TeamShiftInfo, targetUserId: String) {
        let shiftData: [String: Any] = [
            "userId": targetUserId,
            "company": teamShift.company,
            "role": teamShift.role,
            "startTime": teamShift.startTime,
            "endTime": teamShift.endTime,
            "hourlyRate": teamShift.hourlyRate,
            "isGig": false,
            "customEarned": 0.0,
            "reminderBeforeMinutes": 30,
            "isPaid": false,
            "notes": "Team shift: \(teamShift.notes)".trimmingCharacters(in: .whitespaces),
            "bonusApplied": false,
            "bonusAmount": 0.0,
            "teamShiftId": teamShift.id
        ]
        db.collection("shifts").document(UUID().uuidString).setData(shiftData) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.errorMessage = error.localizedDescription
                }
            }
        }
    }

    func updateTeamName(teamId: String, newName: String) {
        db.collection("teams").document(teamId).updateData(["name": newName]) { [weak self] error in
            if error == nil {
                DispatchQueue.main.async {
                    self?.loadTeams()
                }
            } else {
                DispatchQueue.main.async {
                    self?.errorMessage = error?.localizedDescription
                }
            }
        }
    }

    func updateWeeklyCycleStartDay(teamId: String, day: String) {
        db.collection("teams").document(teamId).updateData(["weeklyCycleStartDay": day]) { [weak self] error in
            if error == nil {
                DispatchQueue.main.async {
                    if self?.currentTeam?.id == teamId {
                        self?.currentTeam?.weeklyCycleStartDay = day
                    }
                }
            } else {
                DispatchQueue.main.async {
                    self?.errorMessage = error?.localizedDescription
                }
            }
        }
    }

    func deleteTeam(teamId: String) {
        isLoading = true
        let group = DispatchGroup()
        var deleteError: Error?

        group.enter()
        db.collection("team_members").whereField("teamId", isEqualTo: teamId).getDocuments { [weak self] snapshot, _ in
            if let docs = snapshot?.documents {
                let batch = self?.db.batch()
                docs.forEach { batch?.deleteDocument($0.reference) }
                batch?.commit { error in
                    if let error = error { deleteError = error }
                    group.leave()
                }
            } else {
                group.leave()
            }
        }

        group.enter()
        db.collection("team_shifts").whereField("teamId", isEqualTo: teamId).getDocuments { [weak self] snapshot, _ in
            if let docs = snapshot?.documents {
                let batch = self?.db.batch()
                docs.forEach { batch?.deleteDocument($0.reference) }
                batch?.commit { error in
                    if let error = error { deleteError = error }
                    group.leave()
                }
            } else {
                group.leave()
            }
        }

        group.notify(queue: .main) { [weak self] in
            if let deleteError = deleteError {
                self?.isLoading = false
                self?.errorMessage = deleteError.localizedDescription
                return
            }
            self?.db.collection("teams").document(teamId).delete { error in
                DispatchQueue.main.async {
                    self?.isLoading = false
                    if let error = error {
                        self?.errorMessage = error.localizedDescription
                        return
                    }
                    self?.currentTeam = nil
                    self?.members = []
                    self?.teamShifts = []
                    self?.loadTeams()
                }
            }
        }
    }

    func deleteTeamShift(shiftId: String) {
        teamShifts = teamShifts.filter { $0.id != shiftId }
        db.collection("shifts").whereField("teamShiftId", isEqualTo: shiftId).getDocuments { [weak self] snapshot, _ in
            snapshot?.documents.forEach { $0.reference.delete() }
        }
        db.collection("team_shifts").document(shiftId).delete()
    }

    func promoteMember(memberDocId: String) {
        let previous = members
        if let idx = members.firstIndex(where: { $0.id == memberDocId }) {
            members[idx].role = "manager"
        }
        db.collection("team_members").document(memberDocId).updateData(["role": "manager"]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to promote member: \(error.localizedDescription)"
                }
            }
        }
    }

    func demoteMember(memberDocId: String) {
        let previous = members
        if let idx = members.firstIndex(where: { $0.id == memberDocId }) {
            members[idx].role = "member"
        }
        db.collection("team_members").document(memberDocId).updateData(["role": "member"]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to demote member: \(error.localizedDescription)"
                }
            }
        }
    }

    func removeMember(memberDocId: String, teamId: String) {
        let previous = members
        members.removeAll { $0.id == memberDocId }

        let batch = db.batch()
        batch.deleteDocument(db.collection("team_members").document(memberDocId))
        batch.updateData(["memberCount": FieldValue.increment(Int64(-1))], forDocument: db.collection("teams").document(teamId))

        batch.commit { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to remove member: \(error.localizedDescription)"
                }
            }
        }
    }

    func loadTeamMessages(teamId: String) {
        messagesListener?.remove()
        messagesListener = db.collection("team_messages")
            .whereField("teamId", isEqualTo: teamId)
            .order(by: "createdAt", descending: true)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                let uid = self?.currentUserId
                let oldIds: Set<String> = Set(self?.teamMessages.map { $0.id } ?? [])
                let newMessages: [TeamMessageInfo] = docs.map { doc in
                    let data = doc.data()
                    return TeamMessageInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        senderId: data["senderId"] as? String ?? "",
                        senderName: data["senderName"] as? String ?? "",
                        text: data["text"] as? String ?? "",
                        isAnnouncement: data["isAnnouncement"] as? Bool ?? false,
                        isPinned: data["isPinned"] as? Bool ?? false,
                        imageUrl: data["imageUrl"] as? String ?? "",
                        seenBy: data["seenBy"] as? [String] ?? [],
                        createdAt: (data["createdAt"] as? NSNumber)?.int64Value ?? 0
                    )
                }
                if !oldIds.isEmpty {
                    for msg in newMessages where !oldIds.contains(msg.id) && msg.senderId != uid {
                        let preview = msg.imageUrl.isEmpty ? msg.text : "Sent a photo"
                        self?.postChatNotification(sender: msg.senderName, body: preview)
                    }
                }
                self?.teamMessages = newMessages
            }
    }

    private func postChatNotification(sender: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = "Team Chat – \(currentTeam?.name ?? "Team")"
        content.body = "\(sender): \(body)"
        content.sound = .default
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    func sendMessage(text: String, isAnnouncement: Bool = false) {
        guard let uid = currentUserId, let teamId = currentTeam?.id else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let senderName = currentUserEmail ?? ""
        let data: [String: Any] = [
            "teamId": teamId,
            "senderId": uid,
            "senderName": senderName,
            "text": trimmed,
            "isAnnouncement": isAnnouncement,
            "isPinned": false,
            "createdAt": Int64(Date().timeIntervalSince1970 * 1000)
        ]

        db.collection("team_messages").document(UUID().uuidString).setData(data) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.errorMessage = "Failed to send message: \(error.localizedDescription)"
                }
            }
        }
    }

    func sendImage(_ image: UIImage) {
        guard let uid = currentUserId, let teamId = currentTeam?.id else { return }
        isUploadingImage = true

        let senderName = currentUserEmail ?? ""
        let messageId = UUID().uuidString
        let maxDim: CGFloat = 800
        let scale: CGFloat = min(maxDim / image.size.width, maxDim / image.size.height, 1)
        let newSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let resized: UIImage = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: newSize)) }
        guard let data = resized.jpegData(compressionQuality: 0.5) else {
            isUploadingImage = false
            errorMessage = "Failed to compress image"
            return
        }

        let ref = storageRef.child("chat_images/\(teamId)/\(messageId).jpg")
        ref.putData(data, metadata: nil) { [weak self] _, error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.isUploadingImage = false
                    self?.errorMessage = "Upload failed: \(error.localizedDescription)"
                }
                return
            }
            ref.downloadURL { [weak self] url, _ in
                DispatchQueue.main.async { self?.isUploadingImage = false }
                guard let downloadUrl = url else { return }
                let msgData: [String: Any] = [
                    "teamId": teamId,
                    "senderId": uid,
                    "senderName": senderName,
                    "text": "",
                    "isAnnouncement": false,
                    "isPinned": false,
                    "imageUrl": downloadUrl.absoluteString,
                    "seenBy": [uid],
                    "createdAt": Int64(Date().timeIntervalSince1970 * 1000)
                ]
                self?.db.collection("team_messages").document(messageId).setData(msgData)
            }
        }
    }

    func markMessageSeen(messageId: String) {
        guard let uid = currentUserId else { return }
        let message = teamMessages.first { $0.id == messageId }
        guard let msg = message, !msg.seenBy.contains(uid) else { return }
        db.collection("team_messages").document(messageId).updateData([
            "seenBy": FieldValue.arrayUnion([uid])
        ]) { [weak self] _ in
            self?.checkAutoDelete(messageId: messageId)
        }
    }

    private func checkAutoDelete(messageId: String) {
        let memberCount = members.count
        guard memberCount > 0 else { return }
        db.collection("team_messages").document(messageId).getDocument { [weak self] doc, _ in
            guard let data = doc?.data() else { return }
            let seenBy = data["seenBy"] as? [String] ?? []
            let imageUrl = data["imageUrl"] as? String ?? ""
            if !imageUrl.isEmpty && seenBy.count >= memberCount {
                Storage.storage().reference(forURL: imageUrl).delete(completion: nil)
                self?.db.collection("team_messages").document(messageId).updateData([
                    "imageUrl": "", "text": "Image expired"
                ])
            }
        }
    }

    func deleteMessage(messageId: String) {
        let previous = teamMessages
        let deleted = previous.first { $0.id == messageId }
        if let url = deleted?.imageUrl, !url.isEmpty {
            Storage.storage().reference(forURL: url).delete(completion: nil)
        }
        teamMessages.removeAll { $0.id == messageId }
        db.collection("team_messages").document(messageId).delete { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.teamMessages = previous
                    self?.errorMessage = "Failed to delete: \(error.localizedDescription)"
                }
            }
        }
    }

    func togglePin(messageId: String) {
        guard let idx = teamMessages.firstIndex(where: { $0.id == messageId }) else { return }
        let previous = teamMessages
        let newPinned = !teamMessages[idx].isPinned
        teamMessages[idx].isPinned = newPinned
        db.collection("team_messages").document(messageId).updateData(["isPinned": newPinned]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.teamMessages = previous
                    self?.errorMessage = "Failed to update pin: \(error.localizedDescription)"
                }
            }
        }
    }

    func loadSwapRequests(teamId: String) {
        swapRequestsListener?.remove()
        swapRequestsListener = db.collection("swap_requests")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                self?.swapRequests = docs.map { doc in
                    let data = doc.data()
                    return SwapRequestInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        requesterId: data["requesterId"] as? String ?? "",
                        requesterName: data["requesterName"] as? String ?? "",
                        requesterShiftId: data["requesterShiftId"] as? String ?? "",
                        targetMemberId: data["targetMemberId"] as? String ?? "",
                        targetMemberName: data["targetMemberName"] as? String ?? "",
                        targetShiftId: data["targetShiftId"] as? String ?? "",
                        status: data["status"] as? String ?? "pending",
                        createdAt: (data["createdAt"] as? NSNumber)?.int64Value ?? 0,
                        resolvedAt: (data["resolvedAt"] as? NSNumber)?.int64Value ?? 0,
                        resolvedBy: data["resolvedBy"] as? String ?? ""
                    )
                }.sorted { $0.createdAt > $1.createdAt }
            }
    }

    func requestSwap(myShiftId: String, targetMemberId: String, targetShiftId: String) {
        guard let uid = currentUserId, let teamId = currentTeam?.id else { return }

        let requesterName = currentUserEmail ?? ""
        let targetMember = members.first { $0.userId == targetMemberId }
        let targetName = targetMember?.displayName.isEmpty == false ? targetMember!.displayName : (targetMember?.email ?? "")

        let data: [String: Any] = [
            "teamId": teamId,
            "requesterId": uid,
            "requesterName": requesterName,
            "requesterShiftId": myShiftId,
            "targetMemberId": targetMemberId,
            "targetMemberName": targetName,
            "targetShiftId": targetShiftId,
            "status": "pending",
            "createdAt": Int64(Date().timeIntervalSince1970 * 1000),
            "resolvedAt": Int64(0),
            "resolvedBy": ""
        ]

        db.collection("swap_requests").document(UUID().uuidString).setData(data) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async { self?.errorMessage = "Failed to request swap: \(error.localizedDescription)" }
            }
        }
    }

    func respondToSwap(requestId: String, accept: Bool) {
        guard let uid = currentUserId else { return }
        let newStatus = accept ? "target_accepted" : "declined"
        let previous = swapRequests
        if let idx = swapRequests.firstIndex(where: { $0.id == requestId }) {
            swapRequests[idx].status = newStatus
        }
        var updates: [String: Any] = ["status": newStatus]
        if !accept {
            updates["resolvedBy"] = uid
            updates["resolvedAt"] = Int64(Date().timeIntervalSince1970 * 1000)
        }
        db.collection("swap_requests").document(requestId).updateData(updates) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.swapRequests = previous
                    self?.errorMessage = "Failed to respond: \(error.localizedDescription)"
                }
            }
        }
    }

    func approveSwap(requestId: String, approve: Bool) {
        guard let uid = currentUserId else { return }
        if !approve {
            let previous = swapRequests
            if let idx = swapRequests.firstIndex(where: { $0.id == requestId }) {
                swapRequests[idx].status = "declined"
            }
            db.collection("swap_requests").document(requestId).updateData([
                "status": "declined",
                "resolvedBy": uid,
                "resolvedAt": Int64(Date().timeIntervalSince1970 * 1000)
            ]) { [weak self] error in
                if let error = error {
                    DispatchQueue.main.async {
                        self?.swapRequests = previous
                        self?.errorMessage = "Failed to decline: \(error.localizedDescription)"
                    }
                }
            }
            return
        }

        guard let request = swapRequests.first(where: { $0.id == requestId }) else { return }
        executeSwap(request)
    }

    private func executeSwap(_ request: SwapRequestInfo) {
        guard let uid = currentUserId else { return }
        let batch = db.batch()
        batch.updateData(["assignedTo": request.targetMemberId], forDocument: db.collection("team_shifts").document(request.requesterShiftId))
        batch.updateData(["assignedTo": request.requesterId], forDocument: db.collection("team_shifts").document(request.targetShiftId))
        batch.updateData([
            "status": "approved",
            "resolvedBy": uid,
            "resolvedAt": Int64(Date().timeIntervalSince1970 * 1000)
        ], forDocument: db.collection("swap_requests").document(request.id))

        batch.commit { [weak self] error in
            if let error = error {
                DispatchQueue.main.async { self?.errorMessage = "Failed to execute swap: \(error.localizedDescription)" }
            }
        }
    }

    func cancelSwapRequest(requestId: String) {
        let previous = swapRequests
        swapRequests.removeAll { $0.id == requestId }
        db.collection("swap_requests").document(requestId).delete { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.swapRequests = previous
                    self?.errorMessage = "Failed to cancel: \(error.localizedDescription)"
                }
            }
        }
    }

    func updateShiftStatus(shiftId: String, newStatus: String) {
        let previous = teamShifts
        if let idx = teamShifts.firstIndex(where: { $0.id == shiftId }) {
            teamShifts[idx].status = newStatus
        }
        db.collection("team_shifts").document(shiftId).updateData(["status": newStatus]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.teamShifts = previous
                    self?.errorMessage = "Failed to update shift status: \(error.localizedDescription)"
                }
            }
        }
    }

    func leaveTeam(teamId: String) {
        guard let uid = currentUserId else { return }
        db.collection("team_members")
            .whereField("teamId", isEqualTo: teamId)
            .whereField("userId", isEqualTo: uid)
            .getDocuments { [weak self] snapshot, _ in
                guard let doc = snapshot?.documents.first else { return }
                let batch = self?.db.batch()
                batch?.deleteDocument(doc.reference)
                batch?.updateData(["memberCount": FieldValue.increment(Int64(-1))], forDocument: self?.db.collection("teams").document(teamId) ?? self!.db.collection("teams").document(teamId))
                batch?.commit { error in
                    DispatchQueue.main.async {
                        if let error = error {
                            self?.errorMessage = error.localizedDescription
                            return
                        }
                        self?.currentTeam = nil
                        self?.loadTeams()
                    }
                }
            }
    }

    func removeAllListeners() {
        teamsListener?.remove()
        membersListener?.remove()
        shiftsListener?.remove()
        memberJobsListener?.remove()
        messagesListener?.remove()
        swapRequestsListener?.remove()
    }

    private func generateInviteCode() -> String {
        let chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return String((0..<6).map { _ in chars.randomElement()! })
    }
}
