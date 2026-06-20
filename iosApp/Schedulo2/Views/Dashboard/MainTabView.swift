import SwiftUI

struct MainTabView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @EnvironmentObject var teamViewModel: TeamViewModel

    @State private var selectedTab = 0
    @State private var showAddMenu = false
    @State private var showAddShift = false
    @State private var showWeekPlan = false
    @State private var showProfile = false
    @State private var showInsights = false
    @State private var showJobsView = false
    @State private var editingShiftId: String?

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch selectedTab {
                case 0:
                    DashboardView(
                        onEditShift: { id in editingShiftId = id; showAddShift = true },
                        onNavigateToProfile: { showProfile = true },
                        onNavigateToPay: { selectedTab = 2 }
                    )
                case 1:
                    PlanView(
                        onEditShift: { id in editingShiftId = id; showAddShift = true },
                        onAddShift: { editingShiftId = nil; showAddShift = true }
                    )
                case 2:
                    PayView()
                case 3:
                    TeamView()
                default:
                    EmptyView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .bottom) {
                HStack {
                    TabBarButton(icon: "house.fill", label: "Home", isSelected: selectedTab == 0) {
                        selectedTab = 0
                    }
                    TabBarButton(icon: "calendar", label: "Plan", isSelected: selectedTab == 1) {
                        selectedTab = 1
                    }
                    Spacer().frame(width: 56)
                    TabBarButton(icon: "dollarsign.circle.fill", label: "Pay", isSelected: selectedTab == 2) {
                        selectedTab = 2
                    }
                    TabBarButton(icon: "person.3.fill", label: "Team", isSelected: selectedTab == 3) {
                        selectedTab = 3
                    }
                }
                .padding(.horizontal, 8)
                .frame(height: 64)
                .background(
                    Rectangle()
                        .fill(Color(UIColor.systemBackground))
                        .shadow(color: .black.opacity(0.08), radius: 8, y: -2)
                )
            }

            // Floating add button
            Button(action: { showAddMenu = true }) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(
                            LinearGradient(
                                colors: [.primaryGreen, Color(red: 0.106, green: 0.263, blue: 0.196)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 56, height: 56)
                        .shadow(color: .black.opacity(0.2), radius: 8, y: 4)

                    Image(systemName: "plus")
                        .font(.system(size: 24, weight: .medium))
                        .foregroundColor(.white)
                }
            }
            .padding(.bottom, 20)
            .confirmationDialog("Add Shift", isPresented: $showAddMenu) {
                Button("Add Single Shift") {
                    editingShiftId = nil
                    showAddShift = true
                }
                Button("Plan Entire Week") {
                    showWeekPlan = true
                }
                Button("Cancel", role: .cancel) {}
            }
        }
        .sheet(isPresented: $showAddShift) {
            NavigationStack {
                AddShiftView(shiftId: editingShiftId)
                    .environmentObject(dashboardViewModel)
            }
        }
        .sheet(isPresented: $showWeekPlan) {
            NavigationStack {
                WeekPlanView()
                    .environmentObject(dashboardViewModel)
            }
        }
        .sheet(isPresented: $showProfile) {
            ProfileView(
                onNavigateToInsights: {
                    showProfile = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        showInsights = true
                    }
                },
                onNavigateToJobs: {
                    showProfile = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        showJobsView = true
                    }
                }
            )
            .environmentObject(authViewModel)
            .environmentObject(dashboardViewModel)
        }
        .sheet(isPresented: $showInsights) {
            InsightsView()
                .environmentObject(dashboardViewModel)
        }
        .sheet(isPresented: $showJobsView) {
            NavigationStack {
                JobsView()
                    .environmentObject(dashboardViewModel)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Done") { showJobsView = false }
                        }
                    }
            }
        }
    }
}

private struct TabBarButton: View {
    let icon: String
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(isSelected ? Color.primaryGreen.opacity(0.1) : Color.clear)
                    )
                Text(label)
                    .font(.system(size: 11, weight: isSelected ? .bold : .medium))
            }
            .foregroundColor(isSelected ? .primaryGreen : .secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
