## Team Project Week 10 Update

**July 6 – July 13**

**[Ronald]** implemented household management and permissions,  added household creation with unique invite codes and real membership by replacing the single hardcoded demo house that was originally implemented, and built the roles system where the household creator becomes admin, can promote other members to admin, and only admins can create chores.

**[Michael]** implemented authentication and the data foundation by replacing the name-only sign-in with Firebase login setup for sign-up, sign-in and also built out the user identity model with unique user IDs, email, and display name, and migrated the app off in-memory data to Room so chores, users, and households now persist between sessions.

**[Shoheb]** implemented schedule privacy and chore-completion tracking making each user's schedule private based on the feedback from the professor so people can only view their own as that was indicated during the presentation for safety reasaons, and added per-user tracking of how many chores each person has completed to support fairer recommendations.

**[Kevin]** worked on the chore workflow redesign, he started implementing recommendation-based assignments using availability, chore history, and effort level, along with the self-pickup bulletin board and the chore trading feature where a user can send a direct trade request that another user can accept or deny.

**[Aryan]** worked on scheduling and calendar improvements. He began implementing a full calendar with real month/year date support to replace the current Monday–Sunday grid, as well as the ability to import school calendars so users don't have to enter their classes by hand

**[Happy]** worked on the notification system and started setting up local notifications for new chore pickups, new bulletin board posts, due-soon reminders, and overdue chore alerts.
