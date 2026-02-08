# Software Engineering at Google

## <mark style="color:yellow;">Chapter 1 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">What Is Software Engineering</mark>_&#x20;

#### **1. Key Themes in Software Engineering**

Software engineering differs from programming in three critical dimensions:

* **Time**
* **Scale**
* **Trade-offs**

These distinctions are crucial for software projects because engineers need to consider:

1. **Time**: Over time, systems must adapt to change.
2. **Scale**: As systems grow, maintaining efficiency and handling complex operations across many engineers becomes critical.
3. **Trade-offs**: Engineers must make complex decisions about which path offers the best long-term outcomes.

**2. Programming Integrated Over Time**

At Google, software engineering is described as "programming integrated over time." While programming focuses on creating new software, engineering addresses how that code can be maintained, modified, and updated over time. This involves considering factors like technical debt and the lifespan of code.

* A key question is: _What is the expected lifespan of the code?_
* For short-term projects, technical debt might not be a critical issue, but long-term projects require foresight in handling future changes in technology or business needs【8:0†source】【8:10†source】.

**3. Scale and Team Collaboration**

Software engineering often involves many people and multiple versions of the software. The complexity grows with the number of people involved and how they collaborate to maintain the software over time. The challenge lies in creating systems that can scale both in terms of the number of users and contributors.

* A famous quote defines software engineering as _“the multiperson development of multiversion programs”_【8:0†source】【8:11†source】.

**4. Trade-offs and Decision Complexity**

In software engineering, decisions often have long-term consequences, making it essential to evaluate trade-offs, such as:

* Short-term gains vs. long-term costs
* Immediate scalability vs. deferred maintenance

Good decision-making requires balancing the current benefits with future needs, especially in terms of scale and growth【8:0†source】【8:5†source】.

**5. Sustainability**

Sustainability in software engineering refers to the ability to maintain and modify software to adapt to change. It’s not just about making changes but being _capable_ of doing so when necessary. This capability must be built into the project from the start.

If an organization cannot handle a required change (whether technical or business-driven), it risks failure in the long term【8:0†source】【8:12†source】.

**6. Differences Between Software Engineering and Programming**

The book emphasizes that software engineering is not inherently superior to programming; rather, they address different needs:

* **Programming** deals with short-term code creation.
* **Software Engineering** focuses on maintaining the software’s value over time.

**7. Conclusion and TL;DR**

* Software engineering differs from programming due to its focus on time, scale, and complex trade-offs.
* A successful project must be sustainable and capable of responding to changes over time.
* Long-term sustainability requires thoughtful trade-offs and scalable processes, with an emphasis on team collaboration and planning for growth【8:9†source】【8:10†source】.

## <mark style="color:yellow;">Chapter 2 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">How to Work Well on Teams</mark>_ <mark style="color:yellow;"></mark><mark style="color:yellow;">from</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">Software Engineering at Google</mark>_

**1. Teamwork in Software Engineering**

Software engineering is not a solo endeavor. Effective teamwork is the foundation for success in large-scale, long-term software projects. Google's approach emphasizes that collaboration maximizes individual and collective efforts.

Key points:

* **Avoid hiding code**: Transparency is essential. Holding back code leads to inefficiency and isolation. Share ideas and progress early, even when the work isn't perfect, to avoid wasting time on incorrect assumptions【8:0†source】.

**2. The Genius Myth**

The idea of the lone genius who single-handedly creates successful software is misleading. Software like Linux and Python might have started with individual creators, but they became massive projects because of community contributions and collective effort. Team collaboration, not individual brilliance, drives lasting success【8:0†source】.

**3. The Risks of Isolation**

Isolation in software engineering leads to slower progress and potential project failure. Collaboration helps:

* Identify errors faster
* Share solutions to common problems
* Ensure the project evolves in alignment with current needs【8:3†source】.

**4. The Bus Factor**

The _Bus Factor_ refers to the number of people that need to be unavailable (or "hit by a bus") for a project to come to a halt. Teams should ensure that no single individual holds all critical knowledge of a project. Encouraging documentation, knowledge sharing, and code ownership helps improve this factor.

**5. The Three Pillars of Social Interaction**

The three foundational principles that guide collaboration within teams are:

* **Humility**: Recognizing that you're not the center of the universe. You're open to self-improvement and acknowledge the value others bring to the table.
* **Respect**: Genuinely valuing others' contributions and treating them with kindness.
* **Trust**: Believing in the competence of others, allowing them to lead or make decisions when appropriate【8:0†source】【8:5†source】.

A lack of these pillars often leads to conflict or dysfunction within teams. Healthy teamwork thrives on their consistent practice】.

**6. Blameless Post-Mortems**

Failures and mistakes are inevitable. Google practices _blameless post-mortems_, where the focus is not on assigning blame but on understanding what went wrong and how to prevent it from happening again. This process involves:

* Documenting what happened
* Analyzing the root cause
* Creating action items to fix and prevent similar issues
* Ensuring lessons learned are applied【8:17†source】【8:0†source】.

**7. Importance of Feedback Loops**

Quick feedback loops are essential in both coding and project management. Waiting too long to gather feedback risks wasting time and resources. Continuous feedback ensures that projects stay aligned with evolving requirements and avoid obsolescence【8:10†source】.

**8. Collaborative Culture**

Software engineering at Google values creating a positive, high-functioning team culture, where the collective team effort surpasses the sum of individual contributions. Healthy teams share responsibilities, distribute knowledge, and rely on each other to solve problems【8:10†source】【8:17†source】.

#### Conclusion

* **Teamwork** is at the heart of successful software engineering.
* Emphasize **transparency**, **collaboration**, and **early feedback** to mitigate risks.
* Foster an environment built on **humility**, **respect**, and **trust** to maintain long-term team success.

These principles and practices help teams avoid common pitfalls and ensure steady, long-term progress in complex projects.



## <mark style="color:yellow;">Chapter 3 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">Knowledge Sharing</mark>_ <mark style="color:yellow;"></mark><mark style="color:yellow;">from</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">Software Engineering at Google</mark>_

**1. Challenges to Learning in Organizations**

Effective knowledge sharing across an organization can be difficult due to several challenges, especially as the organization scales. These challenges include:

* **Lack of psychological safety**: If people fear being punished for making mistakes or taking risks, they avoid asking questions or sharing knowledge openly.
* **Information islands**: When teams don’t communicate effectively, each may develop unique practices, leading to knowledge fragmentation, duplication, and skewed processes.
* **Single Point of Failure (SPOF)**: A bottleneck where only one person holds critical knowledge. SPOFs arise when teams rely too much on individual expertise instead of sharing it【8:0†source】【8:2†source】.

**2. Importance of Psychological Safety**

Google emphasizes _psychological safety_ as crucial to promoting a learning environment. Teams where people feel comfortable asking questions, admitting gaps in knowledge, and learning from failure are more likely to thrive. Psychological safety allows for honest knowledge exchange and helps prevent the isolation of information within certain individuals or groups【8:0†source】.

**3. Mentorship and One-to-One Learning**

At Google, mentorship is formalized to support new engineers (Nooglers) and create psychological safety. Mentors answer questions, guide newcomers, and help them understand company culture. While one-to-one help is invaluable, it doesn’t scale well. As a result, Google encourages combining personalized guidance with written documentation【8:1†source】【8:6†source】.

**4. Growing Your Knowledge**

The core advice in this chapter is simple: always ask questions. Learning is an ongoing process, and engineers at Google are encouraged to embrace gaps in knowledge as opportunities to grow rather than weaknesses to hide. Engineers, regardless of their experience, should never hesitate to seek help or guidance from others【8:11†source】.

**5. Scaling Knowledge through Documentation**

One of the most effective ways to scale knowledge sharing is through documentation. Documenting solutions, processes, and learnings ensures that others can benefit from that knowledge long after the initial problem is solved. Google emphasizes creating canonical sources of information that can be used across the organization【8:0†source】【8:9†source】.

**6. Community-based Learning**

Knowledge-sharing tools at Google include group chats, mailing lists, and question-and-answer platforms (like an internal version of Stack Overflow called YAQS). These platforms help engineers tap into the collective knowledge of a broader community and scale learning across the organization【8:10†source】【8:17†source】.

**7. Incentives and Recognition**

A knowledge-sharing culture requires not only formal structures but also recognition and incentives to encourage the behavior. At Google, peer bonuses and kudos serve as a way for engineers to acknowledge and reward their colleagues for going above and beyond in sharing their expertise【8:0†source】【8:9†source】.

**8. Readability Process as Mentorship**

The _readability_ process at Google standardizes code reviews as a form of mentoring. Certified reviewers provide feedback on coding style, best practices, and more, helping engineers improve their craft. This process promotes knowledge sharing by encouraging collaboration and continuous improvement across teams【8:4†source】【8:5†source】.

#### Conclusion

Knowledge sharing is essential for scaling a successful engineering organization. Encouraging a culture of openness, documentation, and continuous learning ensures that expertise is distributed effectively and promotes long-term organizational success. This culture must be nurtured from the top down through incentives and from the bottom up through community-driven practices【8:12†source】【8:16†source】.





## <mark style="color:yellow;">Chapter 4 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">Engineering for Equity</mark>_

**1. Engineering for Equity Overview**

This chapter discusses the responsibilities of engineers in creating products that serve diverse user groups while mitigating harm to underrepresented populations. It emphasizes that software engineering should consider societal impacts and actively work to create equitable solutions .

**2. Bias is the Default**

Bias is inherently present in human thinking, and engineers must acknowledge that even the most well-intentioned efforts can still result in biased outcomes. Unconscious bias often manifests in software products, particularly when developers overlook certain groups due to lack of diversity in the workforce. For example, Google's Photos product failed to properly recognize racial diversity, misclassifying people of color in harmful ways .

**3. The Importance of Diversity in Product Development**

To mitigate bias and improve equity, it is crucial to involve diverse perspectives in all stages of product development. Without diversity, products risk being non-inclusive and potentially harmful. Diverse engineering teams help to prevent such biases and create systems that are more representative of a global user base .

**4. Building Multicultural Capacity**

An exceptional engineer should not only possess technical skills but also the ability to understand how technology can both advantage and disadvantage different groups. This requires a conscious effort to extend one's focus beyond the immediate community and consider the needs of underrepresented users, particularly in large-scale, global software solutions .

**5. Making Diversity Actionable**

It's important for organizations to move beyond philosophical discussions about diversity and take actionable steps. These include:

* Ensuring that hiring pools are diverse.
* Providing equitable opportunities for professional growth within the organization.
* Regularly evaluating the inclusiveness of company policies and systems .

**6. Rejecting Singular Approaches**

Solving systemic inequity requires rejecting one-size-fits-all solutions. For example, focusing solely on fixing the hiring pipeline won't address retention or systemic bias in employee progression. It’s critical to challenge ingrained processes and build systems that are inclusive from the start .

**7. Equity in Product Design**

When designing products, Google stresses the need to prioritize the most vulnerable users. Building with these users in mind improves overall product quality and accessibility. It’s also important to continually measure and monitor equity to ensure that the system remains inclusive throughout its lifecycle .

**8. Case Studies and Lessons Learned**

* **Google Photos Failure**: In 2015, the image recognition system in Google Photos misidentified Black users as “gorillas.” This incident highlighted how insufficient datasets and lack of diversity in product testing could lead to significant failures. It served as a wake-up call for the industry to invest in more comprehensive and inclusive development practices .
* **Equity in Internal Processes**: Google’s internal studies have shown that past performance ratings often do not predict future success when employees move to different teams. A re-evaluation of such systems can lead to more equitable internal mobility .

**9. Values Versus Outcomes**

While Google's values emphasize diversity and inclusion, the implementation of these ideals has not always succeeded. Challenges like underrepresentation of minority groups and products that fail to serve diverse populations illustrate the need for continuous evaluation of both company culture and product outcomes .

**10. Conclusion**

Engineering for equity is a dynamic, ongoing process. Companies must embrace diversity, not just to enhance representation but to ensure that the systems and products they create benefit all users. This requires a commitment to continuous improvement, openness to feedback, and a willingness to acknowledge and correct failures .

#### Key Takeaways

* Bias is the default, and diversity is necessary to combat it.
* Inclusivity should be prioritized from hiring practices to product design.
* Measuring equity and challenging traditional processes are critical to driving systemic change.
* Equity-driven product design ensures better outcomes for all users.

This chapter encourages engineers to focus on building for the most marginalized users and to embrace continuous improvement to create a fairer, more inclusive technological landscape.



## <mark style="color:yellow;">Chapter 5 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">How to Lead a Team</mark>_

**1. The Roles: Managers and Tech Leads**

In an engineering team, leadership roles are divided primarily into two: the **Manager** and the **Tech Lead**.

* **Manager**: Responsible for managing the team, ensuring productivity, and keeping the team aligned with business goals.
* **Tech Lead**: Oversees technical decisions, architecture, and technical direction. They often still contribute as individual contributors.
* **Tech Lead Manager (TLM)**: In smaller or nascent teams, a single person may take on both roles【8:9†source】【8:16†source】.

**2. Transition from Individual Contributor to Leadership**

Many engineers move into leadership roles, often by accident, through resolving conflicts or coordinating work. This transition involves significant changes, like spending less time coding and more time managing people【8:17†source】【8:16†source】.

Key Challenges:

* Feeling responsible for having all the answers, but it’s important to learn that a good leader doesn’t need to know everything【8:19†source】.
* Management work often takes time to show results, unlike the immediate feedback from coding【8:17†source】.

**3. Servant Leadership**

The best leaders at Google follow principles of **servant leadership**, where they focus on supporting and enabling their teams rather than controlling them. This means:

* Giving autonomy to team members.
* Allowing space for growth and development.
* Trusting team members to make decisions【8:17†source】【8:18†source】.

**4. Antipatterns to Avoid**

Some common leadership pitfalls (antipatterns) include:

* **Hiring pushovers**: Avoid hiring people who don’t challenge you. Hire smarter people, even if it means they will challenge your authority【8:14†source】.
* **Ignoring low performers**: Address underperformance early. Letting it slide can demoralize the rest of the team【8:14†source】.
* **Being everyone’s friend**: It’s important to maintain professional relationships and not confuse friendship with leadership. Establish clear boundaries while maintaining rapport【8:15†source】.

**5. Positive Leadership Patterns**

Key leadership practices that Google values:

* **Losing the ego**: As a leader, being humble is crucial. Apologize when you make mistakes and focus on team success rather than individual recognition【8:19†source】【8:10†source】.
* **Being a Zen master**: Stay calm under pressure. Teams take cues from their leaders, so maintaining calm helps the team remain focused during crises【8:19†source】【8:10†source】.
* **Removing roadblocks**: A leader’s role is often to remove obstacles that prevent the team from moving forward, whether they are technical, organizational, or personal【8:5†source】【8:12†source】.
* **Being a teacher and mentor**: It’s important to give your team members room to grow. Allow them to learn by doing, even if it means they may take longer to complete tasks【8:12†source】【8:6†source】.

**6. Tracking Team Happiness**

A good leader monitors the happiness and growth of their team. This includes giving opportunities for skill development and recognizing when someone is doing well【8:4†source】.

**7. Shielding the Team from Chaos**

One of the key roles of a leader is to protect the team from unnecessary organizational distractions or chaos. Provide them with enough information to do their job without overburdening them with unnecessary stress【8:11†source】.

#### Conclusion

* Leadership in software engineering focuses more on enabling the team, removing roadblocks, and promoting a culture of humility, respect, and trust.
* Avoid common leadership mistakes and prioritize fostering team growth, autonomy, and purpose.
* Transitioning from individual contributor to leadership requires adjusting your mindset and focusing on team outcomes rather than individual contributions【8:18†source】【8:19†source】.

These principles guide leadership at Google, helping teams navigate complex technical and interpersonal challenges.



## <mark style="color:yellow;">Chapter 6 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">Leading at Scale</mark>_

**1. Always Be Deciding**

Leading at scale requires constant decision-making at higher levels, focusing more on strategy rather than specific technical problems. This involves:

* **Trade-offs**: Decisions are rarely straightforward and often involve balancing trade-offs. Leaders must evaluate multiple competing priorities.
* **Iterative Process**: Decision-making is ongoing, with the understanding that decisions can be revised and iterated on as more information becomes available .

**2. Always Be Leaving**

The concept of _Always Be Leaving_ emphasizes building a self-sustaining team that can function without the leader’s constant presence. Key strategies include:

* **Delegation**: Assigning subproblems to leaders within the team to ensure they can solve issues independently. This strengthens leadership at all levels and reduces single points of failure (SPOF).
* **Building a “Self-Driving” Team**: Effective leaders work to make themselves dispensable by developing strong leaders within the team, allowing them to move on to new challenges without the organization faltering .

**3. Always Be Scaling**

As responsibilities grow, leaders must learn to scale themselves efficiently. This involves:

* **Cycle of Success**: The leadership process moves through phases of analysis, struggle, traction, and reward. With each successful cycle, new problems arise that require even greater efficiency.
* **Compression of Responsibilities**: Scaling means managing more problems with fewer resources over time. It’s essential to compress responsibilities without burning out, by delegating and trusting team leaders .

**4. Important vs. Urgent**

Leaders often find themselves dealing with urgent tasks at the expense of important, long-term goals. To address this:

* **Delegate Urgent Tasks**: Many urgent tasks can be delegated to others, freeing the leader to focus on more strategic work.
* **Time Management**: Leaders should schedule blocks of time dedicated solely to important, non-urgent tasks such as strategic planning and leadership development .

**5. Learn to Drop Balls**

As a leader, it’s inevitable that not all tasks will be completed. Therefore, it's important to:

* **Prioritize**: Identify the top 20% of critical tasks that only the leader can handle, and delegate or drop the remaining 80%.
* **Trust the Process**: Sometimes tasks that seem urgent will be picked up by others or return to the leader if truly critical .

**6. Protecting Your Energy**

Effective leadership requires managing personal energy as well as time. Key strategies include:

* **Breaks and Downtime**: Regular short breaks, true weekends, and vacations without work distractions are vital for recharging.
* **Mental Health Days**: Leaders should recognize when they are having an off day and take time off to avoid negatively impacting the team .

#### Conclusion

Leadership at scale is about managing teams that grow in complexity and responsibility over time. By focusing on making effective, iterative decisions, building self-sufficient teams, and managing personal resources of time and energy, leaders can effectively scale their impact without becoming overwhelmed .



## <mark style="color:yellow;">Chapter 7 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">Measuring Engineering Productivity</mark>_

**1. Why Measure Engineering Productivity?**

As organizations grow, communication costs and complexity increase exponentially. To maintain and expand efficiently, organizations like Google focus on improving the productivity of individual engineers. Google formed a dedicated research team to study engineering productivity by measuring both the technical and human aspects of software development【8:0†source】.

**2. The Triage Process: Is It Worth Measuring?**

Before deciding to measure, Google evaluates whether the cost of measuring is justified. Measurement takes resources, and without clear actions based on the data, it may be wasted effort. Key questions include:

* **What do you expect to happen?** Understanding biases is crucial.
* **What action will be taken if the data supports your expectations?** If no action will be taken, it’s not worth measuring.
* **Who will decide based on the data, and when?** The person requesting the measurement must be empowered to act【8:1†source】【8:15†source】.

**3. Selecting Meaningful Metrics: The Goals, Signals, and Metrics (GSM) Framework**

Google employs the **GSM framework** to ensure productive measurements:

* **Goals**: The desired end result.
* **Signals**: Indicators that a goal is being achieved, though not all signals are measurable.
* **Metrics**: Proxies for signals—specific data points that can be measured. Metrics help avoid focusing solely on easily accessible but irrelevant data【8:9†source】【8:10†source】.

Example from Google’s readability process:

* **Goal**: Improve code quality.
* **Signal**: Engineers who have readability write higher-quality code.
* **Metric**: Percentage of engineers who report satisfaction with their code quality【8:13†source】【8:14†source】.

**4. The QUANTS Components**

Google measures productivity using five components (mnemonic: **QUANTS**):

* **Quality of the code**: Are test cases preventing regressions? Is architecture robust?
* **Attention from engineers**: Are engineers frequently in a state of flow, free from distractions?
* **Intellectual complexity**: How much cognitive load is required? Is unnecessary complexity reduced?
* **Tempo and velocity**: How quickly can engineers complete tasks?
* **Satisfaction**: How satisfied are engineers with their tools and work environment?【8:12†source】.

**5. Data Validation and Mixed Methods**

Data alone can be misleading, so Google triangulates data through mixed methods:

* **Logs data**: Objective measures of process time and performance.
* **Surveys**: Self-reported experiences of engineers.
* **Interviews and case studies**: Contextual and anecdotal insights【8:18†source】【8:7†source】.

**6. Taking Action and Tracking Results**

Once data is gathered and validated, the team creates actionable recommendations, such as improving tools or processes. For example, the readability process was found to be generally beneficial, but engineers provided feedback that led to tool improvements and faster reviews【8:6†source】.

**7. Conclusion**

Measuring engineering productivity is essential for scaling effectively. Using the GSM framework ensures that metrics are aligned with actual goals, and a combination of qualitative and quantitative data leads to informed, actionable insights【8:10†source】【8:6†source】.



## <mark style="color:yellow;">Chapter 8 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">Style Guides and Rules</mark>_

**1. Purpose of Style Guides**

Style guides at Google serve as rules that govern the development of code across various programming languages. These are not just formatting guidelines, but essential rules to maintain consistency, readability, and sustainability across a massive, evolving codebase. Rules are mandatory, whereas guidance offers best practices with some flexibility.

* **Rules**: Mandatory laws that all engineers must follow to ensure uniformity.
* **Guidance**: Best practices that are advisable but not strictly enforced【8:0†source】【8:9†source】.

**2. Why Have Rules?**

The primary goal of rules is to maintain a codebase that is sustainable and maintainable over time. They enforce good behavior and discourage bad practices. For example, promoting consistency makes it easier for engineers to move between projects and understand each other’s code【8:0†source】【8:13†source】.

* **Consistency**: One of the most critical aspects of the style guides, consistency helps ensure that engineers can easily read, understand, and modify code written by others.
* **Reducing Cognitive Load**: By establishing common conventions, engineers don’t have to expend mental effort on trivial matters like formatting or naming conventions. This allows them to focus on problem-solving【8:16†source】【8:19†source】.

**3. Categories of Rules**

The rules in style guides are divided into three main categories:

* **Avoiding Danger**: These rules focus on avoiding language features or practices that could lead to bugs or other issues. For example, handling exceptions, managing threading, and static members.
* **Enforcing Best Practices**: These rules guide engineers on writing healthier and more maintainable code. For instance, conventions on comments, file structuring, and variable naming ensure better documentation and maintainability【8:7†source】【8:19†source】.
* **Ensuring Consistency**: These rules aim to ensure uniformity in code, including indentation, line length, and naming conventions. The focus here is to minimize debates over minor details and ensure engineers follow the same approach【8:12†source】【8:19†source】.

**4. Adapting to New Features**

As languages evolve, new features often introduce potential pitfalls. Google initially restricts their use while observing how engineers apply them in practice. Over time, they update the style guide based on observed best practices and feedback from engineers【8:15†source】.

* **Example**: When C++11 introduced `std::unique_ptr`, it was initially disallowed due to its unfamiliarity. Over time, as engineers adjusted to its implications, it became widely adopted due to the benefits of clearer object ownership and cleaner code【8:18†source】.

**5. Exceptions to the Rules**

While rules are strict, exceptions are sometimes allowed when they offer clear benefits. For instance:

* **Macro Naming**: C++ macros usually require a project-specific prefix to avoid naming conflicts, but certain utility macros can be exempted.
* **Implicit Conversions**: In C++, the style guide generally prohibits implicit type conversions but allows exceptions when dealing with types that transparently wrap other types【8:8†source】【8:18†source】.

**6. Automated Enforcement**

Automating the enforcement of style rules ensures consistency across the organization. Tools like formatters and linters help enforce compliance without relying on manual checking, which could be inconsistent and prone to human error【8:17†source】【8:19†source】.

**7. Training and Readability Process**

Google has formal processes for training engineers on style rules. The _readability_ process, where experienced engineers review others' code, helps instill these rules across projects. It ensures that engineers adopt and internalize good practices over time【8:11†source】【8:17†source】.

#### Conclusion

Style guides are essential at Google to maintain a vast and evolving codebase. By focusing on consistency, avoiding dangerous patterns, and enforcing best practices, these rules keep the codebase readable, maintainable, and scalable. Additionally, the guides are adaptable, allowing exceptions where necessary and evolving with language features. The use of automated tools ensures these rules are applied efficiently, reducing the cognitive load on engineers【8:7†source】【8:13†source】.



## <mark style="color:yellow;">Chapter 9 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">Code Review</mark>_

**1. The Purpose of Code Review**

Code review is the process of having another engineer review code before it is committed to the codebase. It helps ensure correctness, comprehension, consistency, and facilitates knowledge sharing among team members. At Google, all changes undergo review to maintain the health of the codebase .

**2. Benefits of Code Review**

* **Correctness**: Code review checks whether the code works as intended, preventing bugs.
* **Comprehension**: Reviewing ensures that the code is understandable by other engineers, as code is often read many more times than it is written.
* **Consistency**: Ensures uniformity in style and structure across the codebase, which makes the code easier to maintain and scale.
* **Knowledge Sharing**: Code review promotes knowledge transfer within teams and provides a historical record of the reasoning behind code changes .

**3. Best Practices in Code Review**

* **Politeness and Professionalism**: Reviewers should offer constructive feedback, ask questions before making assumptions, and treat the process as a learning opportunity for both the reviewer and author.
* **Prompt Feedback**: Reviewers are expected to provide feedback within 24 working hours to keep the process agile. Authors should also be receptive to suggestions and understand that the code belongs to the team .
* **Small Changes**: Smaller changes are easier to review, lead to faster iteration, and make it easier to track bugs if they arise .

**4. The Code Review Flow**

The review process follows these steps:

1. **Author Submits Change**: The author writes and uploads the change to the review tool.
2. **Reviewer Comments**: The reviewer inspects the code and leaves feedback. Comments can be requests for change or suggestions for improvement.
3. **Modifications**: The author makes changes based on feedback and resubmits the code. This cycle may repeat until the reviewer is satisfied.
4. **LGTM Approval**: Once the reviewer deems the code acceptable, they mark it as “looks good to me” (LGTM), allowing it to be committed .

**5. Types of Code Reviews**

* **Greenfield Code Reviews**: These involve reviewing entirely new code. The focus is on ensuring long-term maintainability and adherence to a predetermined design .
* **Behavioral Changes and Optimizations**: Most code changes fall into this category, involving improvements, API modifications, or performance optimizations .
* **Bug Fixes**: Bug fixes should be atomic and focus solely on resolving the issue to make rollback easier .
* **Refactorings and Large-Scale Changes**: Often automated, these changes require careful review to ensure they do not introduce unexpected issues .

**6. Automation in Code Review**

Automation tools are used to catch mechanical issues such as style violations or simple bugs before the reviewer even looks at the code. This allows human reviewers to focus on higher-level issues like design and functionality. Google uses tools like presubmits to handle these tasks .

**7. Conclusion**

Code review is a vital part of Google's development process, ensuring that code is correct, consistent, and maintainable. It encourages collaboration, knowledge sharing, and provides a structured historical record of code evolution .

By adhering to these best practices, Google optimizes for speed and code quality, while minimizing friction in the development process.





## <mark style="color:yellow;">Chapter 10 -</mark> <mark style="color:yellow;"></mark>_<mark style="color:yellow;">Documentation</mark>_

**1. Importance of Documentation**

Documentation is critical for effective software development. It helps answer key questions about the code, such as design decisions, functionality, and expected behavior. Despite its importance, engineers often undervalue documentation because its benefits are often not immediate but accrue over time. Well-documented code aids in comprehension, reduces errors, and accelerates onboarding of new team members【8:19†source】.

**2. What Qualifies as Documentation?**

In software engineering, documentation refers to a wide range of materials that help clarify the workings of a system. This includes:

* **Code comments**: These are the most common form of documentation, especially for describing functions or APIs.
* **Standalone documents**: Such as tutorials, design documents, and project pages. Each type serves a distinct purpose and should not be mixed within a single document【8:0†source】.

**3. Documentation is Like Code**

Documentation should be treated similarly to code. This means it should:

* Have clear ownership
* Be placed under source control
* Undergo regular reviews
* Be periodically evaluated for accuracy and relevance Just like code, documentation can become stale and must be maintained【8:16†source】【8:19†source】.

**4. Know Your Audience**

Writing effective documentation requires understanding the needs of your audience. Different types of readers require different approaches:

* **Experts**: They may need concise information or deeper technical explanations.
* **Novices**: They require more context and clarity. Documentation should aim to serve both these groups without overloading the document【8:19†source】.

**5. Types of Documentation**

The chapter identifies several distinct types of documents:

* **Reference Documentation**: Includes code comments that describe how specific parts of the system work. Code comments are further split into API comments (focused on the public interface) and implementation comments (focused on internal logic).
* **Design Documents**: These describe how a system is structured and provide a historical record of decisions.
* **Tutorials**: Aimed at onboarding new users, tutorials help individuals quickly learn how to use a system. "Hello World" tutorials are particularly effective for this purpose.
* **Conceptual Documentation**: These documents explain the concepts behind a system or API, often providing broader context and guiding principles.
* **Landing Pages**: Used as entry points for more complex documentation sets, landing pages help users find relevant information more easily【8:0†source】【8:4†source】【8:17†source】.

**6. Documentation Reviews**

Documentation, like code, benefits from review. Google recommends three types of review:

* **Technical Review**: Ensures accuracy, usually by a subject-matter expert.
* **Audience Review**: Ensures clarity and readability, ideally by someone less familiar with the system.
* **Writing Review**: Ensures consistency and coherence, often handled by a technical writer【8:4†source】.

**7. Challenges in Maintaining Documentation**

One of the key challenges is preventing documentation from becoming obsolete. This can be tackled by:

* Assigning clear ownership to documents
* Using "freshness dates" to ensure regular updates
* Marking outdated documents as deprecated or removing them entirely【8:4†source】【8:19†source】.

**8. Documentation as a Tool**

Documentation should be viewed as a powerful tool, just like a programming language. It has its own rules, syntax, and style. Properly written documentation provides significant long-term benefits, making it easier for future readers to understand and contribute to the code【8:16†source】.

#### Conclusion

Documentation is a vital part of software development. By treating it as part of the codebase, regularly reviewing it, and focusing on the needs of diverse audiences, engineers can ensure that documentation remains accurate, useful, and sustainable【8:16†source】.





## <mark style="color:yellow;">Chapter 11: Testing Overview</mark>

**1. Introduction to Testing at Google**

* Automated testing is fundamental to software development at Google. It helps ensure code quality, minimizes bugs, and facilitates easier code modifications.
* The chapter provides insights into how Google thinks about testing and its approach to building scalable and maintainable test suites.

**2. Why Do We Write Tests?**

* The primary reason for writing tests is to catch bugs early and ensure the quality of the code.
* Testing prevents regressions when new changes are made and improves the reliability of the system.
* A robust test suite gives developers confidence to refactor code and make changes without fear of breaking existing functionality【10:11†source】.

**3. The Story of Google Web Server (GWS)**

* The chapter recounts how the Google Web Server (GWS) improved significantly after adopting rigorous testing practices.
* GWS initially suffered from frequent issues and bugs that impacted the reliability of Google Search.
* Implementing automated tests transformed GWS, allowing it to detect problems early and stabilize the system【10:11†source】.

**4. Testing at the Speed of Modern Development**

* Modern software development involves multiple releases per day, making manual testing impractical.
* Automated testing helps manage the increasing complexity and frequency of releases by testing various configurations and use cases at scale【10:11†source】.

**5. Write, Run, React Cycle**

* Testing involves three core activities: **writing tests**, **running tests**, and **reacting to test failures**.
  * **Writing tests**: Developers create tests to validate specific functionality or system behaviors.
  * **Running tests**: Tests are automated and run continuously to validate new changes.
  * **Reacting**: Developers address test failures as soon as they occur to maintain code stability【10:11†source】.

**6. Benefits of Testing Code**

* Testing ensures code correctness, maintains system reliability, and provides documentation for how code should work.
* Well-written tests can serve as examples or guides for using certain functionalities【10:11†source】.

**7. The Beyoncé Rule**

* The chapter humorously refers to the "Beyoncé Rule": "If you liked it, you should have put a test on it." This emphasizes the importance of testing every piece of code you write【10:1†source】.

**8. Designing a Test Suite**

* A good test suite should cover a range of test types, including unit tests, integration tests, and end-to-end tests.
* The distribution of these test types should typically follow a **test pyramid**:
  * **Unit Tests** (80%): Small, fast, and validate individual functions or classes.
  * **Integration Tests** (15%): Medium scope, validate interactions between components.
  * **End-to-End Tests** (5%): Large scope, validate the entire system, but tend to be slower【10:13†source】.

**9. Test Size and Scope**

* **Test Size** refers to the resources consumed by a test and its complexity.
* **Test Scope** refers to how much code a test validates. It’s generally preferred to have tests of narrow scope (e.g., validating a single method or class)【10:13†source】.

**10. Pitfalls of a Large Test Suite**

* Large test suites can become unmanageable and difficult to maintain. Tests that are too slow, unreliable, or complex may hinder development.
* To avoid these pitfalls, focus on creating fast, deterministic tests that provide immediate feedback【10:10†source】.

**11. Testing Culture at Google**

* Google emphasizes a strong testing culture with practices like "Testing on the Toilet" (short articles on testing best practices posted in restrooms) to promote good testing habits.
* Engineers are encouraged to prioritize fixing broken tests and maintaining high test coverage【10:11†source】.

**12. Conclusion and Key Takeaways**

* Automated testing is foundational for enabling continuous software development.
* For tests to be effective at scale, they must be automated, maintainable, and have a balanced mix of different test types.
* Changing testing culture and practices in an organization takes time, but the benefits to code quality and team productivity are substantial【10:1†source】.





## <mark style="color:yellow;">Chapter 12: Unit Testing</mark>

**1. Importance of Unit Testing**

* Unit tests are crucial for ensuring that individual components of software work as expected. They focus on validating the behavior of small, isolated parts of the system, typically a single class or function.
* Unit tests are usually the smallest in size compared to other types of tests (e.g., integration tests) and are characterized by their narrow scope.
* Google encourages developers to aim for 80% of their tests being unit tests, as they provide fast feedback and are easy to write and maintain .

**2. Characteristics of Good Unit Tests**

* **Fast and Deterministic**: They should run quickly and produce the same results each time.
* **High Test Coverage**: By covering many edge cases, unit tests make it easier to detect when changes break existing functionality.
* **Clarity**: Well-written unit tests are easy to understand, making it simple to identify which functionality is broken when a test fails.
* **Documentation**: They serve as an example of how to use the tested class or method and explain what the system should do under various conditions .

**3. Writing Unit Tests Using Public APIs**

* Google advocates writing tests that interact with the system only through its public APIs.
* Testing through public APIs ensures that tests remain valid even when the internal implementation of the class changes.
* This approach makes unit tests less brittle and easier to maintain because they don’t depend on the internal workings of the class .

**4. Preventing Brittle Tests**

* Brittle tests are those that break frequently due to minor changes in implementation.
* To prevent brittle tests:
  * Avoid testing private methods directly.
  * Use the system’s public API to test expected outcomes.
  * Ensure that tests validate state and behavior rather than specific method calls or internal logic .

**5. DAMP vs. DRY Testing**

* **DAMP** (Descriptive and Meaningful Phrases) over **DRY** (Don’t Repeat Yourself) is preferred when writing test code.
  * Avoid overly abstract test code that attempts to minimize repetition by making use of complex helper methods.
  * While DRY is good for production code, it can make test code hard to understand and maintain.
* Make test names and assertions clear and self-explanatory, even if this means some repetition in test code .

**6. Defining Test Infrastructure**

* Shared test infrastructure can improve the efficiency of writing and running tests.
* Google recommends defining test infrastructure only when absolutely necessary and treating it as a separate product with its own test coverage.
* Using standardized third-party libraries (e.g., JUnit for Java) for testing infrastructure is encouraged to maintain consistency across the organization .

**7. Test Behaviors, Not Methods**

* Write tests that validate the expected behavior rather than testing specific methods or their implementations.
* This ensures that tests remain relevant even if the internal implementation changes, as long as the behavior of the class or function remains the same .

**8. Clear Failure Messages**

* Make failure messages in unit tests informative so that developers can quickly understand what went wrong without having to look into the test code itself.
* The messages should provide context about what was being tested, what the expected outcome was, and what actually happened .

**9. Avoid Putting Logic in Tests**

* Tests should not contain complex logic like loops or conditionals.
* The purpose of a test is to validate logic, not to introduce new logic that can be a source of bugs and make the test harder to understand .

**10. Unit Tests as Documentation**

* Unit tests serve as a form of documentation for the system. They show how different parts of the system are expected to behave and how to interact with the code.
* Good unit tests can teach new developers how to use the system and understand its constraints .

**11. Conclusion and Key Takeaways**

* Unit tests are the backbone of a stable and reliable software system. When written correctly, they allow for rapid development and confidence in making changes.
* Google’s guidelines focus on writing robust, maintainable unit tests that interact only through public APIs and prioritize clear, descriptive code over overly concise, DRY principles .

These notes summarize the key insights and principles discussed in Chapter 12 of the book.



## <mark style="color:yellow;">Chapter 13: Test Doubles</mark>&#x20;

#### **1. Introduction to Test Doubles**

* Test doubles are objects or functions that stand in for real implementations in tests. They simulate the behavior of real components, allowing developers to isolate the code being tested and validate its behavior without relying on external systems.
* Test doubles are useful for complex systems where using real implementations can be time-consuming or impractical, such as when interacting with external servers or databases.

**2. Benefits of Using Test Doubles**

* Test doubles make it easier to test code that depends on external systems, such as databases or web services, by simulating these dependencies in a controlled manner.
* They help improve the testability of code by allowing developers to simulate different scenarios, such as failures or edge cases, that would be difficult to reproduce using real systems.
* Using test doubles can reduce test flakiness and improve the speed of test execution【19:5†source】.

**3. Types of Test Doubles**

* **Dummy**: Objects that are passed around but never used. They are usually just placeholders.
* **Fake**: Lightweight implementations of an API that behave like the real implementation but are not suitable for production. For example, an in-memory database used in place of a real database.
* **Stub**: Objects or functions that provide predefined responses to method calls made during the test. Stubs are used to control the behavior of the code under test by returning specific values or triggering specific actions.
* **Spy**: Objects that record information about the interactions they have, such as the number of times a method was called and with what arguments.
* **Mock**: Objects that are pre-programmed with expectations and allow you to specify the expected interactions with them. Mocks are typically used for interaction testing【19:5†source】.

**4. Test Doubles at Google**

* Google has seen both positive and negative impacts from the use of test doubles. While test doubles can increase productivity and software quality, improper use can lead to brittle, complex, and less effective tests.
* Historically, Google used mocking frameworks extensively. However, as the limitations of these approaches became evident, teams began to favor writing more realistic tests and reducing reliance on mocks【19:15†source】.

**5. Techniques for Using Test Doubles**

* **Faking**: A fake is a simple implementation of a component that behaves like the real component but is not suitable for production use. Fakes are ideal for testing functionality without incurring the overhead of using a real system.
  * Example: An in-memory database that mimics a real database for testing purposes.
  * Fakes should be maintained to ensure they remain accurate representations of the real systems they replace【19:19†source】.
* **Stubbing**: Stubbing involves setting specific return values for method calls. It allows you to control the behavior of a component during a test.
  * Example: Using a stubbed method to always return a particular user object in a test scenario.
  * While stubbing can be helpful, overuse can lead to tests that are tightly coupled to specific implementations, making them difficult to maintain【19:19†source】.
* **Interaction Testing**: Interaction testing verifies how an object is used rather than its state. This involves checking whether certain methods are called with expected arguments or a specific number of times.
  * Example: Validating that a `sendEmail()` method is called exactly once during a transaction.
  * This type of testing is often used with mocking frameworks like Mockito but should be used sparingly to avoid complex and brittle tests【19:19†source】.

**6. Best Practices for Using Test Doubles**

* Prefer state testing over interaction testing: State testing validates the state and output of a system, while interaction testing focuses on internal method calls and dependencies.
* Use real implementations wherever possible: If using a real implementation is practical and doesn’t negatively impact the test’s performance or reliability, prefer it over a test double. Real implementations tend to be more reliable and less prone to issues during refactoring.
* Use fakes when real implementations are impractical: Fakes are often the best balance between realism and performance.
* Minimize the use of stubs and mocks: These should only be used when necessary, as they can lead to fragile tests that require constant maintenance.

**7. Dependency Injection and Seams**

* **Dependency Injection**: A technique for making code more testable by passing dependencies (such as external services or components) to classes rather than instantiating them directly.
  * This enables swapping out real implementations for test doubles in unit tests, making tests more isolated and easier to manage.
  * Google uses frameworks like Guice and Dagger for dependency injection in Java codebases.
* **Seams**: Seams are points in the code where test doubles can be inserted. They make it possible to substitute real dependencies with test doubles, making the code more modular and testable.
  * Example: A constructor seam allows you to pass in a mock service during testing instead of using the actual service implementation【19:17†source】.

**8. Common Pitfalls of Using Test Doubles**

* Overuse of mocking frameworks can lead to tests that require significant effort to maintain and update.
* Tests that rely too heavily on internal implementation details are prone to breaking when refactoring, even if the overall behavior of the system remains unchanged.
* Developers should be cautious when designing test doubles to ensure they don’t introduce complexity or reduce the effectiveness of the test suite【19:15†source】.

**9. Conclusion and Key Takeaways**

* Test doubles are powerful tools for creating robust and maintainable test suites, but their use must be balanced and appropriate.
* Google’s approach has evolved over time, focusing on realism and reducing unnecessary complexity in tests by minimizing reliance on mocks and using real implementations whenever feasible【19:15†source】.

These notes provide a detailed summary of Chapter 13's discussions on test doubles, their types, use cases, and best practices at Google.



## <mark style="color:yellow;">Chapter 14: Larger Testing</mark>

**1. Introduction to Larger Testing**

* Larger tests, as described in Chapter 14, encompass tests that go beyond the constraints of small, unit tests. While unit tests are restricted to one thread, process, and machine, larger tests often span multiple processes, machines, and sometimes even external systems.
* These tests are critical for validating the overall system's functionality, ensuring that different components interact correctly in more complex scenarios.
* Larger tests are necessary to catch integration and system-level issues that unit tests cannot detect, providing a more comprehensive evaluation of software behavior【19:19†source】.

**2. What Are Larger Tests?**

* Larger tests differ from small tests in both size and scope:
  * **Size**: Large tests can be multi-threaded, involve multiple processes, or even run across multiple machines.
  * **Scope**: They cover broader interactions between components, verifying that the entire system works as expected.
* These tests are often referred to as **integration tests**, **system tests**, or **end-to-end tests**【19:19†source】.

**3. Characteristics of Larger Tests**

* Larger tests can exhibit various characteristics that make them fundamentally different from smaller tests:
  * **Slow Execution**: Large tests may take significantly longer to run, with timeouts set at 15 minutes to 1 hour, and in some cases, even days.
  * **Nonhermetic Nature**: Unlike unit tests, larger tests might share resources with other tests or traffic, leading to non-isolated conditions.
  * **Nondeterministic Behavior**: Due to shared resources, large tests can produce non-reproducible results depending on external factors, making them less reliable in some cases【19:19†source】.

**4. Importance of Fidelity in Testing**

* **Fidelity** refers to how accurately a test reflects the real-world environment in which the software operates.
  * Higher fidelity tests, such as those run in production environments, provide the highest assurance of correctness but come with increased risk and cost.
  * Balancing fidelity and cost is crucial: as fidelity increases, so do the complexity and the difficulty of maintaining the test【19:19†source】.

**5. Types of Larger Tests**

* **Functional Testing of Interacting Binaries**: Validates that multiple binaries interact correctly when running together.
* **Browser and Device Testing**: Tests software on different browsers or devices to ensure compatibility and performance.
* **Performance, Load, and Stress Testing**: Measures how well the system performs under various load conditions.
* **Deployment Configuration Testing**: Ensures that different deployment configurations do not cause unexpected issues.
* **A/B Diff Regression Testing**: Compares two versions of the system to identify any regressions.
* **User Acceptance Testing (UAT)**: Validates the system from the end-user perspective to ensure it meets requirements and expectations.
* **Probers and Canary Analysis**: Tests for system health in production environments by monitoring small subsets of traffic.
* **Disaster Recovery and Chaos Engineering**: Evaluates how well the system handles failures and recoveries【19:19†source】.

**6. Larger Tests at Google**

* At Google, larger tests play a critical role in maintaining software quality across the massive scale and complexity of their systems.
* While larger tests are valuable, their maintainability is a significant concern. The complexity of running and maintaining these tests increases as the system grows.
* There is also a risk of the "ice cream cone" antipattern, where the testing structure becomes top-heavy with too many end-to-end tests and insufficient unit or integration tests【19:19†source】.

**7. Structure of a Large Test**

* A large test generally consists of three key components:
  * **System Under Test (SUT)**: The set of software components or systems being tested.
  * **Test Data**: Realistic data sets that help simulate the real-world scenarios in which the SUT operates.
  * **Verification**: The process of checking that the system behaves as expected during the test【19:19†source】.

**8. Challenges with Larger Tests**

* **Execution Time**: Longer tests can slow down the development cycle and impact productivity.
* **Nondeterministic Failures**: Flaky tests can lead to non-deterministic failures, which reduce confidence in the test results.
* **Resource Intensive**: Running larger tests can require significant computational and storage resources, making them expensive and challenging to manage【19:19†source】.

**9. Best Practices for Larger Tests**

* Minimize the reliance on large tests for basic functionality checks; these should be covered by smaller tests.
* Use larger tests to validate complex interactions and integration points that cannot be simulated by smaller tests.
* Keep large tests as isolated and deterministic as possible by controlling the environment and dependencies【19:19†source】.

**10. Conclusion and Key Takeaways**

* While larger tests are essential for ensuring that complex systems behave correctly in real-world scenarios, they should be used judiciously and designed carefully to avoid becoming resource sinks.
* Google’s approach emphasizes balancing test fidelity with maintainability and performance, ensuring that larger tests provide value without overwhelming the development process【19:19†source】.

These notes provide a comprehensive overview of Chapter 14, highlighting the significance of larger tests, their characteristics, types, and the best practices recommended by Google.



## <mark style="color:yellow;">Chapter 15: Deprecation</mark>

**1. Introduction to Deprecation**

* Deprecation refers to the process of phasing out a system or a feature that is no longer considered viable or necessary. It is a crucial practice for maintaining the long-term sustainability of software systems.
* At Google, deprecation is viewed as an essential activity to manage technical debt and to keep systems manageable, scalable, and maintainable .

**2. Why Deprecate?**

* **Code is a Liability**: Unlike common beliefs that code is an asset, at Google, code is seen as a liability. This view is based on the ongoing costs associated with maintaining, testing, and evolving a codebase over time.
  * Keeping unnecessary or outdated systems can hinder the progress of new systems, as developers have to ensure compatibility and maintain integration with older systems .
* **Avoid Redundancy and Complexity**: Deprecation is usually carried out when there are redundant systems that serve the same purpose or when newer systems offer better efficiency, security, or maintainability .
* **Long-Term Sustainability**: Removing obsolete systems allows a codebase to evolve faster and helps focus engineering efforts on maintaining fewer, more efficient systems .

**3. Why Is Deprecation So Hard?**

* Deprecating a system involves social and technical challenges, making it a complex and difficult process:
  * **Social Challenges**: Users may resist deprecation due to familiarity with the existing system or dependencies on certain features.
  * **Technical Challenges**: Interdependencies between systems can make it difficult to deprecate one system without affecting others. The codebase may have built up technical debt, which complicates the process of identifying dependencies and assessing the impact of deprecation .

**4. Types of Deprecation**

* **Advisory Deprecation**: Indicates that a system or feature is planned to be deprecated, but it is not enforced. This allows users to start planning their transition without immediate consequences.
* **Compulsory Deprecation**: Enforces the deprecation of a system or feature, typically with a hard deadline. Users must stop using the deprecated feature and migrate to the replacement.
* **Deprecation Warnings**: Warnings are given when deprecated features are used, but they do not yet enforce the removal of the feature .

**5. Managing the Deprecation Process**

* **Process Owners**: Designate owners who are responsible for overseeing the deprecation process, including communication, coordination, and support for affected users.
* **Milestones**: Set clear milestones to guide the deprecation process, such as advisory notices, usage reduction targets, and final removal deadlines.
* **Deprecation Tooling**: Invest in tools that help identify usage of deprecated systems and enforce policy. Automated tools can help detect dependencies, assess impacts, and manage communication .

**6. Deprecation During Design**

* Google advocates considering deprecation as a potential outcome during the design phase itself. This includes designing systems with clear exit strategies and building features that are easy to turn down when they become obsolete.
* By planning for deprecation during the design phase, developers can build systems that are more modular and easier to maintain or replace in the future .

**7. Challenges in Evaluating Deprecation Costs**

* Evaluating the costs associated with deprecation is difficult because they often extend beyond the immediate technical considerations. They include:
  * **Maintenance Costs**: Direct costs of keeping a system operational and updating it as dependencies change.
  * **Ecosystem Costs**: The drag on feature development due to the need to maintain compatibility with deprecated systems.
  * **User Transition Costs**: The cost and effort required for users to migrate to a new system, which might include training, rewriting integrations, and updating configurations .

**8. Best Practices for Deprecation**

* **Communicate Early and Often**: Ensure that users are informed about upcoming deprecations well in advance, along with clear justifications and timelines.
* **Provide Adequate Support and Migration Paths**: Help users transition smoothly by providing documentation, tools, and resources.
* **Incorporate Deprecation into the Development Cycle**: Treat deprecation as a normal part of the software lifecycle rather than a last resort .

**9. Conclusion and Key Takeaways**

* Deprecation is a necessary practice to manage the complexity of large software systems and ensure long-term sustainability.
* Google’s approach emphasizes careful planning, clear communication, and supportive tools to manage the social and technical challenges of deprecation.
* By treating code as a liability and planning for deprecation early, organizations can maintain healthier, more maintainable codebases .

These notes provide a comprehensive overview of Chapter 15, covering the rationale, challenges, and best practices for deprecating systems at Google.





## <mark style="color:yellow;">Chapter 16: Version Control and Branch Management</mark>

**1. Introduction to Version Control**

* Version Control Systems (VCS) are fundamental to software engineering, allowing teams to manage changes to source code over time. They enable collaboration, track history, and facilitate rollback in case of errors.
* The chapter explores different version control strategies, including centralized and distributed models, and discusses branch management in the context of a large organization like Google.

**2. Why Is Version Control Important?**

* Version control helps maintain a **single source of truth** for the entire codebase, ensuring consistency and stability across the development process.
* It enables teams to **track changes** and understand the evolution of the software.
* With version control, developers can work independently on features or bug fixes without disrupting each other's work. This is particularly important when multiple teams or engineers are working on the same codebase simultaneously【19:16†source】.

**3. Centralized VCS vs. Distributed VCS**

* **Centralized VCS**: All version history is stored in a single location (e.g., Subversion). It’s easier to maintain but can become a bottleneck as the team grows.
* **Distributed VCS**: Each developer has a complete copy of the repository (e.g., Git). This model supports offline work and better collaboration but may lead to diverging changes that are difficult to merge back into the main branch【19:16†source】.

**4. Branch Management**

* Effective branch management is critical in large-scale software projects to control the complexity introduced by multiple developers working on different parts of the system.
* Google emphasizes the use of trunk-based development over long-lived branches:
  * **Trunk-Based Development**: Developers commit code directly to the trunk (main branch), with minimal use of feature or development branches.
  * **Dev Branches**: Used temporarily for testing or experimenting. Google prefers to keep them short-lived to avoid integration issues【19:18†source】.

**5. The “One-Version” Rule**

* At Google, there is a preference for maintaining a **single version** of a library or service at any point in time, known as the “One-Version” rule.
* This strategy minimizes compatibility issues and simplifies dependency management, ensuring that all services and applications are using the same version of a library.

**6. Avoiding Long-Lived Branches**

* Long-lived branches can lead to integration problems, conflicts, and technical debt. Google prefers to limit the use of long-lived branches and instead encourages continuous integration and regular commits to the main branch【19:16†source】.

**7. Monorepos at Google**

* Google uses a monorepo approach where the majority of its codebase is stored in a single repository. This strategy has several benefits:
  * **Unified Source of Truth**: All changes are visible and accessible in one place, reducing redundancy and making it easier to manage dependencies.
  * **Consistent Development Practices**: Using a monorepo allows Google to enforce consistent practices like code reviews, testing, and version control policies across all projects.
  * **Simplified Refactoring**: It’s easier to make sweeping changes across the entire codebase because all the code is in one place【19:16†source】.

**8. Work in Progress as Branches**

* Any uncommitted work in progress (WIP) should be considered equivalent to a branch. Even with a centralized VCS, WIP that hasn’t been committed is essentially a branch that could diverge from the main codebase.
* By acknowledging this concept, developers can treat their local changes with the same discipline as they would with committed branches, ensuring consistency and reducing integration challenges【19:18†source】.

**9. Challenges of Version Control at Scale**

* Managing a version control system at the scale of Google involves unique challenges:
  * **Performance**: The system must handle thousands of engineers working on millions of lines of code.
  * **Consistency**: Keeping all the changes synchronized across such a large codebase is non-trivial.
  * **Dependency Management**: Ensuring that changes in one part of the system do not break dependencies elsewhere in the codebase requires automated testing and strong version control policies【19:16†source】.

**10. Future of Version Control**

* The chapter concludes with a discussion on the potential evolution of version control systems, including improvements in:
  * Better support for large-scale changes across repositories.
  * Enhanced tooling to manage complex dependencies.
  * More robust systems for ensuring the consistency and integrity of codebases as they grow in size and complexity【19:16†source】.

These notes summarize the key points and principles discussed in Chapter 16, focusing on version control and branch management strategies, as well as Google's unique approach to managing a large and complex codebase using a monorepo.





## <mark style="color:yellow;">Chapter 17: Code Search</mark>

**1. Introduction to Code Search**

* Code Search is an essential tool at Google that allows engineers to explore and navigate the codebase effectively. It serves as a specialized web tool for finding and understanding code, providing a global view of the entire repository without the need for local checkouts or setups.
* The tool is designed to work at scale, enabling developers to perform complex searches across Google's massive codebase, making it a critical part of the development workflow.

**2. The Code Search UI**

* The Code Search interface is optimized for browsing and understanding code rather than editing it, which is typically handled by an IDE.
* The UI is tailored to support specific developer needs, such as jumping to symbol definitions, viewing file histories, and inspecting dependencies.

**3. How Do Googlers Use Code Search?**

* Google engineers use Code Search in various ways to locate and understand code. This includes finding examples of how APIs are used, checking references to specific symbols, and exploring the history of changes in the code.
* The tool allows for quick navigation between related files and definitions, making it easier for developers to understand unfamiliar parts of the codebase.

**4. Why a Separate Web Tool?**

* Unlike traditional IDEs, Code Search is not constrained by editing functionalities. This allows it to focus on providing a streamlined experience for code exploration.
* Because it’s a web-based tool, Code Search can leverage Google’s infrastructure to provide a zero-setup environment with real-time indexing and search capabilities.
* Integration with other developer tools is also a key feature, allowing users to link errors, logs, and stack traces directly back to the source code in Code Search【19:18†source】.

**5. Impact of Scale on Design**

* **Search Query Latency**: Given the size of Google’s codebase, search query latency is a significant consideration. The system is optimized to provide fast results even when querying large amounts of data.
* **Index Latency**: The indexing system ensures that newly committed changes are searchable within a reasonable timeframe. This minimizes the gap between code submission and discoverability in the tool【19:18†source】.

**6. Google’s Implementation of Code Search**

* The backend of Code Search is designed to handle the vast scale of Google's codebase. It indexes all the code and supports complex queries like substring and regular expression searches.
* The search results are ranked based on relevance, using metrics like file popularity and the frequency of symbols.

**7. Selected Trade-Offs in Code Search**

* **Completeness**: Code Search can be configured to prioritize "repository at head" (the latest version of files) over older branches and commits. This means users get the most relevant results for active development but might miss historical information.
* **Expressiveness**: The tool supports different levels of query expressiveness, ranging from simple token searches to complex regular expressions, allowing developers to fine-tune their search criteria as needed【19:18†source】.

**8. Integration with Other Tools**

* Code Search integrates seamlessly with other developer tools at Google, such as log viewers and error reporting systems. This allows developers to trace logs or errors back to the source code quickly.
* It is often used alongside documentation and codelabs, with links to source code references and examples embedded in learning materials.

**9. Conclusion and Key Takeaways**

* Code Search is a specialized tool designed to meet the unique needs of developers working at scale. It provides an efficient way to browse and understand the codebase, making it easier to maintain and improve large software projects.
* The tool's integration with other systems and its ability to handle large-scale searches make it a cornerstone of Google's development practices【19:18†source】.

These notes provide an overview of the functionality, design, and use cases of Code Search as outlined in Chapter 17, highlighting its role and importance within Google's engineering environment.



## <mark style="color:yellow;">Chapter 18: Build Systems and Build Philosophy</mark>

**1. Introduction to Build Systems**

* A build system is an integral part of software development that automates the process of compiling source code into executable programs or libraries. It manages dependencies, organizes files, and ensures that the correct versions are built and deployed.
* This chapter focuses on modern build systems, their characteristics, and best practices in managing complex build processes at scale.

**2. Purpose of a Build System**

* The primary goal of a build system is to transform source code into a deliverable artifact. This involves compiling code, linking libraries, running tests, and packaging the final software.
* Build systems are essential for ensuring that code changes are correctly integrated and tested, allowing teams to maintain consistent quality and reliability.

**3. What Happens Without a Build System?**

* Without a build system, development teams may rely on manual processes like shell scripts or ad-hoc commands, which can lead to errors and inefficiencies.
* Manual builds are prone to human error, difficult to reproduce, and can lead to inconsistent results. This makes it challenging to manage dependencies and debug issues.

**4. Evolution of Build Systems**

* Early build processes often involved using simple shell scripts, but as projects grew in size and complexity, dedicated build tools like `make`, Ant, and Maven emerged.
* Modern build systems have evolved to handle complex dependencies, optimize build performance, and support large-scale, distributed build environments.

**5. Modern Build Systems**

* **Task-Based Build Systems**: The core unit of work in these systems is a "task" or "target," which can execute a script or a series of commands. Dependencies between tasks are defined, and the build system determines the execution order based on these dependencies.
  * Examples: Ant, Maven, Gradle.
  * Tasks are represented as nodes in a Directed Acyclic Graph (DAG), where edges represent dependencies between tasks【19:17†source】.
* **Artifact-Based Build Systems**: Focus on defining artifacts (e.g., binaries, libraries) rather than individual tasks. The build system tracks how each artifact is produced and manages dependencies between artifacts, making it easier to build complex projects with multiple outputs.

**6. Challenges in Build Systems**

* **Managing Dependencies**: Ensuring that all required libraries and tools are available and compatible can be challenging, especially in large projects with multiple teams.
* **Reproducibility**: Builds should produce the same output given the same inputs, regardless of the environment or build order.
* **Performance**: Large projects can have long build times, so build systems need to optimize for performance by parallelizing tasks and using caching mechanisms【19:18†source】.

**7. Distributed Builds**

* Distributed build systems allow multiple build processes to run on different machines, enabling parallelism and reducing build times for large projects.
* Google uses a distributed build system (e.g., Bazel) that supports massive parallelism, remote execution, and caching to handle the scale and complexity of its codebase.

**8. Task-Based Build Systems in Detail**

* In a task-based build system, tasks are defined with dependencies on other tasks. When a build is initiated, the system creates a DAG of all tasks and their dependencies, ensuring that tasks are executed in the correct order.
* Each task performs a specific build operation, such as compiling code or running tests. Task-based systems are highly flexible and can be used to build projects of varying complexity【19:17†source】.

**9. Managing Dependencies in Modern Build Systems**

* Dependencies can be internal (within the same project) or external (third-party libraries).
* Google employs a "One-Dependency" rule to simplify dependency management, ensuring that all teams use the same version of a library to avoid conflicts and compatibility issues.

**10. Build Philosophy and Best Practices**

* A robust build philosophy ensures that the build process is reliable, maintainable, and scalable. Best practices include:
  * Using fine-grained modules to reduce build times and increase modularity.
  * Minimizing the visibility of modules to prevent unintended dependencies.
  * Managing dependencies explicitly and updating them regularly to avoid technical debt【19:17†source】.

**11. The Role of Build Systems at Google**

* Google’s build system is designed to handle the vast scale of its codebase, with support for distributed builds, remote execution, and dependency management.
* The build system is closely integrated with other tools like version control and continuous integration (CI) to provide a seamless developer experience.

**12. Conclusion and Key Takeaways**

* Build systems are a cornerstone of modern software development, enabling teams to automate the process of transforming source code into deliverable artifacts.
* Effective build systems should support modularity, scalability, and reproducibility, while minimizing complexity and build times.
* Google’s build philosophy emphasizes the importance of maintaining consistency, managing dependencies, and optimizing build performance to support the needs of a large engineering organization【19:18†source】.

These notes provide an overview of the key principles and practices discussed in Chapter 18, covering build systems, their evolution, and Google's approach to managing complex build environments.



## <mark style="color:yellow;">Chapter 19: Critique - Google’s Code Review Tool</mark>

**1. Introduction to Critique**

* Critique is Google's internal code review tool, designed to support and streamline the code review process across Google's extensive codebase.
* Code reviews are an essential part of Google’s software engineering culture, promoting code quality, knowledge sharing, and collaboration.

**2. Code Review Tooling Principles**

* The core principles guiding Critique’s design and implementation are:
  * **Usability**: The tool must be easy to use and support engineers in focusing on the code itself rather than the tooling.
  * **Scalability**: It needs to handle Google’s vast codebase and support thousands of engineers making changes every day.
  * **Integration**: Critique integrates seamlessly with other Google tools, including the version control system and build infrastructure, enabling a cohesive workflow for engineers【19:8†source】.

**3. The Code Review Flow**

* The code review process in Critique consists of several stages, designed to ensure that changes are systematically reviewed and approved before being integrated:
  * **Stage 1: Create a Change**: Engineers initiate a code review by creating a change in the tool. This involves uploading a diff of the proposed changes.
  * **Stage 2: Request Review**: Once the change is created, the author requests a review from a designated reviewer.
  * **Stage 3: Understanding the Change**: The reviewer examines the diff, tests, and any associated analysis results.
  * **Stage 4: Commenting on the Change**: Reviewers can leave comments on specific lines or files, asking for clarifications or suggesting improvements.
  * **Stage 5: Change Approval**: If the reviewer is satisfied, they approve the change by scoring it, indicating that the change is ready for submission.
  * **Stage 6: Committing the Change**: Once approved, the change is submitted and committed to the codebase.
  * **Post-Commit Tracking**: The tool provides mechanisms for tracking the history of changes and understanding their impact over time【19:8†source】.

**4. Features of Critique**

* **Diffing**: Critique provides sophisticated diffing capabilities to show line-by-line changes, making it easy for reviewers to see exactly what has been modified.
* **Tight Tool Integration**: Critique is closely integrated with other tools like the build system and static analysis platforms, allowing it to display build and test results directly in the review tool.
* **Analysis Results**: The tool can display results from automated static analysis, helping reviewers identify potential issues automatically.
* **Notifications**: Engineers receive notifications about review requests and comments, ensuring that code reviews are promptly addressed.

**5. Commenting and Collaboration**

* Critique facilitates collaboration by allowing multiple reviewers to leave comments and discuss changes directly in the tool.
* Comments are typically tied to specific lines or files, making it easier to contextualize feedback and understand the rationale behind requested changes.

**6. Change Approvals**

* Reviewers score a change to indicate their approval or request further modifications.
* Critique enforces a minimum number of approvals for each change, depending on the team’s policy and the nature of the change.

**7. Post-Commit Tracking and History**

* After a change is committed, Critique provides tools to track its history and monitor its effects on the codebase.
* Engineers can easily see who reviewed and approved each change, as well as any subsequent modifications【19:8†source】.

**8. Benefits of Using Critique**

* **Quality Assurance**: Ensures that all code changes are reviewed for quality, style, and potential bugs.
* **Knowledge Sharing**: Promotes knowledge sharing among engineers, as reviewers often learn about parts of the codebase they might not be directly working on.
* **Consistency**: Enforces coding standards and best practices through structured reviews and feedback.

**9. Challenges and Limitations**

* Despite its benefits, the code review process can be time-consuming, especially for complex changes or changes that touch multiple areas of the codebase.
* Managing large numbers of review requests and ensuring timely responses can be challenging for teams.

**10. Conclusion and Key Takeaways**

* Critique plays a pivotal role in Google’s engineering practices by ensuring that code changes are systematically reviewed, approved, and integrated.
* The tool’s integration with other systems and its support for collaboration and quality assurance make it a cornerstone of Google’s development environment【19:8†source】.

These notes summarize the key points discussed in Chapter 19, focusing on the functionality, process, and benefits of Critique, Google's internal code review tool.



## <mark style="color:yellow;">Chapter 20: Static Analysis</mark>

**1. Introduction to Static Analysis**

* Static analysis refers to the automated process of analyzing source code without executing it. It involves inspecting code for potential errors, code quality issues, and adherence to coding standards.
* The primary goal of static analysis is to identify and address bugs, code smells, and other issues early in the development cycle, before the code is even run.
* Google’s use of static analysis aims to improve code quality and maintainability, reducing the cost of finding and fixing issues later in the software lifecycle.

**2. Benefits of Static Analysis**

* **Early Bug Detection**: Detects bugs that might not be caught by traditional testing. It helps identify issues such as null pointer dereferences, unused variables, and uninitialized values.
* **Enforcement of Coding Standards**: Static analysis tools can enforce style guides and coding standards automatically, ensuring consistency across the codebase.
* **Reduced Maintenance Costs**: By catching errors early, static analysis minimizes the effort required to address bugs and prevents the accumulation of technical debt.
* **Improved Code Comprehension**: Tools like linters and analyzers provide feedback that helps developers write clearer and more maintainable code【19:8†source】.

**3. Types of Static Analysis**

* Google uses various types of static analysis techniques depending on the scope and depth of the analysis required:
  * **Style Checkers**: Ensure adherence to code style and format guidelines.
  * **Linters**: Detect syntax issues and simple coding errors.
  * **Semantic Analysis**: Involves understanding the code’s structure and behavior, such as ensuring type safety and control flow correctness.
  * **Security Analysis**: Identifies potential security vulnerabilities, such as SQL injection or cross-site scripting (XSS).

**4. Challenges with Static Analysis**

* **False Positives**: One of the most common issues is false positives, where the tool flags a problem that isn’t actually an issue. Too many false positives can lead to “alert fatigue,” causing developers to ignore important warnings.
* **Performance**: Running complex static analysis tools can be computationally intensive and slow down the development workflow if not handled correctly.
* **Scalability**: As codebases grow in size, running static analysis at scale can become a bottleneck, requiring sophisticated tooling and infrastructure to manage efficiently【19:8†source】.

**5. Google’s Static Analysis Platform: Tricorder**

* Tricorder is Google’s in-house static analysis platform, which integrates multiple static analysis tools and provides a unified interface for developers.
* **Key Features of Tricorder**:
  * **Integrated Tools**: Supports various analyzers for different languages and use cases.
  * **Custom Feedback Channels**: Developers receive results directly in their code review tools or IDEs, making it easy to act on findings.
  * **Suggested Fixes**: Tricorder can provide automated suggestions for fixing certain types of issues, reducing the manual effort required by developers【19:8†source】.

**6. Integration into the Developer Workflow**

* Google emphasizes making static analysis a natural part of the developer workflow rather than an afterthought. Tricorder integrates with Google’s code review tool, Critique, so developers see analysis results in context as they review and submit code changes.
* Analysis results are displayed in real-time or as part of the pre-submit checks, ensuring that issues are addressed before code is merged into the main branch【19:8†source】.

**7. Making Static Analysis Effective**

* For static analysis to be effective at scale, it must balance accuracy (minimizing false positives) with coverage (ensuring relevant issues are flagged).
* **Focus on Developer Experience**: The tools should prioritize developer happiness by providing actionable feedback, integrating seamlessly with the development environment, and minimizing disruptions to workflow.
* **Empower Users to Contribute**: Google allows developers to contribute new rules and checks to Tricorder, enabling the platform to evolve with the needs of the codebase【19:8†source】.

**8. Conclusion and Key Takeaways**

* Static analysis is a powerful tool for improving code quality and reducing long-term maintenance costs, but it requires careful implementation and integration into the development process.
* Google’s Tricorder platform demonstrates how to effectively use static analysis at scale, with a focus on usability, integration, and developer empowerment【19:8†source】.

These notes summarize the main points of Chapter 20, focusing on the benefits, challenges, and best practices of using static analysis tools, along with an overview of Google’s static analysis platform, Tricorder.





## <mark style="color:yellow;">Chapter 21</mark>&#x20;

challenges and approaches to managing dependencies in large-scale software systems. Here are the key points covered in this chapter:

#### Why Dependency Management is Difficult

* Dependency management is not just about managing individual packages but entire networks of dependencies that evolve over time.
* Dependencies can be direct (required by your code) or transitive (indirectly required). Over time, each node in this network updates, requiring efficient strategies to maintain compatibility across a system.
* One of the biggest challenges is finding compatible versions across the entire network when you do not control these dependencies.

#### Conflicting Requirements and Diamond Dependencies

* One common issue is conflicting requirements, especially when two dependencies require different versions of the same third-party library, known as the "diamond dependency" problem.
* Different programming languages deal with this problem differently. For example, Java can handle multiple versions of dependencies through mechanisms like shading, while C++ is much less tolerant due to the One Definition Rule.

#### Importing Dependencies

* Google emphasizes asking key questions when importing dependencies such as the availability of tests, reputation, and compatibility promises of the dependency.
* Google mostly relies on internal dependencies, which simplifies management since they control both the providers and consumers of the dependency.
* When dealing with external dependencies, the process is more formalized, and teams must consider long-term maintenance.

#### <mark style="color:blue;">Dependency Management Models</mark>

1. **Nothing Changes Model**:
   * The most stable but unrealistic model assumes that dependencies do not change, prioritizing stability over all else. However, over a long enough period, this model breaks down due to inevitable security patches and bug fixes.
2. **Semantic Versioning (SemVer)**:
   * SemVer is a widely used strategy where version numbers indicate the type of changes (major, minor, patch), aiming to prevent breaking changes.
   * The challenge is that version-satisfiability across a complex dependency network can be NP-complete, making it difficult to manage over time.
3. **Bundled Distribution Models**:
   * This model, used by systems like Linux distributions, bundles all necessary dependencies and releases them as a single unit.
   * It simplifies dependency management but may introduce problems when dependencies need to be updated outside of the bundled context.
4. **Live at Head**:
   * This is a model Google favors where all projects use the latest version of dependencies ("Live at Head").
   * This approach requires a high level of coordination, as changes are only committed when tests and CI systems validate that they don’t break any dependents.
   * It places the burden on API providers to test changes against the entire ecosystem before committing.

#### Key Considerations for Google’s Approach

* Google tends to prefer solving source control problems rather than dependency management problems. Internal dependencies are easier to manage than external ones, and the company has established policies like the "One-Version Rule" to prevent multiple versions of the same dependency from causing conflicts in its monorepo.

The chapter explores how scale and time exacerbate dependency management issues, providing a deep dive into strategies that Google and others use to maintain large systems over time.



## <mark style="color:yellow;">Chapter 23</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**Continuous Integration (CI)**</mark>

which is a software development practice where team members frequently integrate their work, and each integration is verified by an automated build to detect errors early. Here are the key points covered:

#### <mark style="color:blue;">1.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**CI Concepts**</mark>

* **Fast Feedback Loops:** CI encourages fast feedback loops to catch and resolve issues early in the development cycle. The later a bug is caught, the more expensive it becomes to fix.
* **Automation:** Automation is crucial in CI to ensure efficient, consistent testing and builds. This automation covers both the building of software and its testing.
* **Continuous Testing:** Testing is tightly integrated with CI. The tests need to be selected and optimized depending on the stage of integration, with more comprehensive testing happening as changes move toward production.

#### <mark style="color:blue;">2.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Challenges in CI**</mark>

* **Hermetic Testing:** One of the biggest challenges is ensuring tests are fully hermetic, meaning they are isolated from outside factors (e.g., networks) to provide reliable results. This requires significant infrastructure but ensures greater stability in the tests.

#### <mark style="color:blue;">3.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Google's Approach to CI**</mark>

* Google uses **TAP (Test Automation Platform)**, a global continuous build tool, to handle millions of tests and build processes across its monorepo. TAP integrates changes and runs tests to verify them. Changes that pass are integrated into the codebase.
* **Presubmit and Post-submit Testing:** In presubmit testing, a subset of the tests is run before code review. After submission, TAP runs more comprehensive tests in the background. This two-stage approach allows for faster integration without compromising on testing rigor.
* **Build Cop Role:** Each team has a designated "Build Cop" responsible for addressing test failures quickly. The Build Cop ensures that failing builds do not block progress by either fixing issues or rolling back changes.

#### <mark style="color:blue;">4.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**CI Case Study: Google Takeout**</mark>

* The chapter includes a detailed case study on Google Takeout, which evolved from a small project to a large platform serving multiple Google products. As the project grew, so did the complexity of its configuration and CI requirements.
* Takeout faced issues with configurations breaking different services. To address this, the team created sandboxed environments for testing presubmit changes, which significantly reduced deployment failures. Post-submit testing runs more extensive end-to-end tests to catch issues not caught earlier.

#### <mark style="color:blue;">5.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Benefits of CI**</mark>

* CI helps ensure software is always in a deployable state, reduces the time spent on integration issues, and allows teams to focus more on feature development rather than bug-fixing.
* It improves developer productivity and system stability by automating testing and feedback.

#### <mark style="color:blue;">6.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Key Takeaways**</mark>

* CI should aim to provide actionable, accessible feedback as early as possible.
* The process becomes essential as the codebase grows in complexity.
* Smaller, more focused changes encourage faster testing and integration.

This chapter emphasizes the importance of having a robust CI system, especially in large organizations like Google, where codebases and projects grow rapidly. Efficient CI practices reduce integration problems and allow teams to scale confidently【12:0†source】【12:4†source】【12:17†source】.



## <mark style="color:yellow;">Chapter 22</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**Large-Scale Changes (LSCs)**</mark>

which are modifications that affect a large number of files or codebases, often across an entire organization. These changes are complex and require specialized infrastructure and processes to handle them efficiently. Here are the key points:

#### <mark style="color:blue;">1.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**What is a Large-Scale Change (LSC)?**</mark>

* LSCs are non-trivial, widespread changes that can affect millions of lines of code across numerous projects.
* These changes are needed to address systemic issues such as upgrading APIs, modifying code structures, or deprecating obsolete features across a monorepo.

#### <mark style="color:blue;">2.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Who Deals with LSCs?**</mark>

* **Specialized Teams:** Google has dedicated teams to handle LSCs. These teams consist of engineers who specialize in managing and executing these complex changes.
* **Automated Systems:** A significant portion of LSC work at Google is handled by automation, given the scale of changes required.
* **Ownership and Communication:** It is critical for the team executing the LSC to communicate well with code owners across different projects, ensuring alignment and smooth transitions.

#### <mark style="color:blue;">3.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Barriers to Atomic Changes**</mark>

LSCs can't always be committed atomically for several reasons:

* **Technical Limitations:** The complexity of coordinating such vast changes, potential merge conflicts, and ensuring consistency make it hard to handle all changes at once.
* **Merge Conflicts:** As multiple developers work concurrently, it is challenging to prevent merge conflicts in a monorepo. LSCs exacerbate these conflicts.
* **Heterogeneity:** The diversity of programming languages, tools, and frameworks across a large codebase adds complexity to making large-scale updates.

#### <mark style="color:blue;">4.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Key Challenges of LSCs**</mark>

* **Testing:** Comprehensive testing is critical to ensure that LSCs don’t introduce regressions or break code. At Google, automated testing is heavily utilized to mitigate risks.
* **Code Review:** LSCs require careful code reviews. Given the scale, automated systems handle much of the review process, and special permissions may be required to proceed.
* **No Haunted Graveyards:** It's crucial to avoid leaving behind unused code or orphaned functionality. All changes must be well-tracked and cleaned up if necessary.

#### <mark style="color:blue;">5.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**LSC Infrastructure**</mark>

To manage LSCs efficiently, Google has built an extensive infrastructure, including:

* **Sharded Commits:** Breaking down changes into smaller, manageable chunks that can be submitted in parallel.
* **Batching and Automation:** Tools help automate much of the refactoring work and testing, ensuring changes propagate smoothly without manual intervention.
* **Tracking and Monitoring:** Specialized systems track the progress of LSCs, highlighting any issues or bottlenecks.

#### <mark style="color:blue;">6.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Policies and Culture**</mark>

* **Ownership:** LSCs require careful attention to ownership. Engineers working on LSCs need to coordinate with code owners to ensure changes align with the broader goals of the system.
* **Codebase Insight:** Insight into the entire codebase is crucial for understanding the potential impact of LSCs. Google uses tools that provide visibility into dependencies and help identify which projects will be affected by changes.
* **Change Management:** Changes are managed incrementally to reduce risks and to allow for rolling back if necessary.

#### <mark style="color:blue;">7.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Testing and Review**</mark>

* Automated testing infrastructure at Google is designed to support LSCs. Every large-scale change is accompanied by a thorough suite of tests.
* **Code Review Tools:** Google’s Critique tool facilitates large-scale reviews, allowing teams to comment, track, and approve LSCs across various codebases efficiently.

#### <mark style="color:blue;">8.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Process for LSCs**</mark>

* **Authorization:** Only experienced engineers with a broad understanding of the codebase are authorized to initiate LSCs. This ensures that changes are well-planned and communicated.
* **Change Creation:** LSCs are created in sharded steps to avoid overwhelming the system with one massive change.
* **Sharding and Submitting:** LSCs are submitted in manageable parts or "shards," ensuring that each part can be thoroughly tested before proceeding with the next. This incremental approach mitigates risk.
* **Cleanup:** Once LSCs are implemented, there is a cleanup process to remove deprecated code, ensuring no "haunted graveyards" are left behind.

#### <mark style="color:blue;">9.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**Conclusion**</mark>

* LSCs are a necessity for long-term maintenance and evolution of large systems like Google's codebase.
* Proper infrastructure, policies, and culture are key to handling these changes without causing disruptions.
* Automated systems play a vital role in managing LSCs efficiently, minimizing the impact on developers and ensuring smooth transitions.

#### <mark style="color:blue;">10.</mark> <mark style="color:blue;"></mark><mark style="color:blue;">**TL;DRs**</mark>

* Large-Scale Changes (LSCs) are non-trivial and require both specialized infrastructure and skilled teams to handle them efficiently.
* Google has developed tools, processes, and policies that allow for these changes to be handled incrementally, ensuring minimal disruption.
* Testing and review are critical components in the process of rolling out LSCs, helping ensure system-wide stability.

These notes provide an overview of how Google manages large-scale changes across a vast and interconnected codebase, relying heavily on automation, structured processes, and collaboration between specialized teams and code owners.



## <mark style="color:yellow;">Chapter 23</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**Continuous Integration (CI)**</mark>

which is a software development practice where team members frequently integrate their work, and each integration is verified by an automated build to detect errors early. Here are the key points covered:

#### 1. **CI Concepts**

* **Fast Feedback Loops:** CI encourages fast feedback loops to catch and resolve issues early in the development cycle. The later a bug is caught, the more expensive it becomes to fix.
* **Automation:** Automation is crucial in CI to ensure efficient, consistent testing and builds. This automation covers both the building of software and its testing.
* **Continuous Testing:** Testing is tightly integrated with CI. The tests need to be selected and optimized depending on the stage of integration, with more comprehensive testing happening as changes move toward production.

#### 2. **Challenges in CI**

* **Hermetic Testing:** One of the biggest challenges is ensuring tests are fully hermetic, meaning they are isolated from outside factors (e.g., networks) to provide reliable results. This requires significant infrastructure but ensures greater stability in the tests.

#### 3. **Google's Approach to CI**

* Google uses **TAP (Test Automation Platform)**, a global continuous build tool, to handle millions of tests and build processes across its monorepo. TAP integrates changes and runs tests to verify them. Changes that pass are integrated into the codebase.
* **Presubmit and Post-submit Testing:** In presubmit testing, a subset of the tests is run before code review. After submission, TAP runs more comprehensive tests in the background. This two-stage approach allows for faster integration without compromising on testing rigor.
* **Build Cop Role:** Each team has a designated "Build Cop" responsible for addressing test failures quickly. The Build Cop ensures that failing builds do not block progress by either fixing issues or rolling back changes.

#### 4. **CI Case Study: Google Takeout**

* The chapter includes a detailed case study on Google Takeout, which evolved from a small project to a large platform serving multiple Google products. As the project grew, so did the complexity of its configuration and CI requirements.
* Takeout faced issues with configurations breaking different services. To address this, the team created sandboxed environments for testing presubmit changes, which significantly reduced deployment failures. Post-submit testing runs more extensive end-to-end tests to catch issues not caught earlier.

#### 5. **Benefits of CI**

* CI helps ensure software is always in a deployable state, reduces the time spent on integration issues, and allows teams to focus more on feature development rather than bug-fixing.
* It improves developer productivity and system stability by automating testing and feedback.

#### 6. **Key Takeaways**

* CI should aim to provide actionable, accessible feedback as early as possible.
* The process becomes essential as the codebase grows in complexity.
* Smaller, more focused changes encourage faster testing and integration.

This chapter emphasizes the importance of having a robust CI system, especially in large organizations like Google, where codebases and projects grow rapidly. Efficient CI practices reduce integration problems and allow teams to scale confidently【12:0†source】【12:4†source】【12:17†source】.



## <mark style="color:yellow;">Chapter 24</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**Continuous Delivery (CD)**</mark>

focusing on the importance of rapid deployment and feedback cycles to maintain a competitive edge in software development. Here’s a detailed breakdown of the key concepts:

#### 1. **Why Continuous Delivery?**

* The technology landscape shifts quickly, and the ability to release software rapidly becomes a critical competitive advantage.
* CD allows organizations to deploy features and fixes quickly, reduce the cost of work in progress, and improve developer velocity.
* The earlier software gets in front of users, the faster valuable feedback can be gathered. This allows for quick iterations, leading to better product-market fit.

#### 2. **Core Tenets of Continuous Delivery**

CD at Google emphasizes several principles that help make the process more effective:

* **Agility**: Release frequently and in smaller batches, reducing the risk per release and making troubleshooting easier.
* **Automation**: Automate the repetitive aspects of deployment to allow engineers to focus on valuable work.
* **Isolation**: Use modular architecture to isolate changes, making troubleshooting easier.
* **Reliability**: Continuously measure and improve key health indicators like crashes or latency.
* **Data-Driven Decisions**: Use A/B testing on health metrics to ensure changes positively impact the system.
* **Phased Rollout**: Roll out changes to a subset of users before a full-scale deployment.

#### 3. **Challenges and Benefits of Frequent Releases**

* While it may seem risky to release software frequently, CD actually lowers risk by reducing the number of changes between each release.
* Frequent releases make debugging easier because the changes are smaller, and issues can be identified more easily.

#### 4. **Breaking Deployment into Manageable Pieces**

* As teams grow, they tend to branch off into smaller subteams, which can complicate the integration and release process.
* To manage this, Google prefers that teams continue developing at head (the mainline) in a shared codebase, using Continuous Integration (CI) systems to detect issues early.
* Automatic rollbacks and “culprit finding” tools help identify problems and resolve them quickly.

#### 5. **Flag-Guarding Features**

* Features under development are guarded by flags, which allow them to be turned on or off dynamically without redeploying the software.
* This helps manage risk, as new features can be deployed in a controlled manner and tested in production without affecting all users.

#### 6. **Release Train Model**

* Google operates on a "release train" model, where if a developer misses a release deadline, the feature simply moves to the next release cycle.
* This predictable cadence reduces stress and improves work-life balance for engineers.

#### 7. **Shipping Only What Gets Used**

* Bloat is a common problem in software development, where unused features and code slow down deployments.
* Google focuses on shipping only what is necessary by using dynamic deployments and forcing trade-offs between user value and feature cost. This reduces the overhead on both developers and users.

#### 8. **Shifting Left and Making Data-Driven Decisions**

* The concept of "shifting left" involves making decisions earlier in the development process based on data.
* Google uses A/B testing and staged rollouts to gather real-time data on feature performance, ensuring that only successful features are deployed to all users.

#### 9. **Changing Team Culture**

* CD requires cultural changes within the organization, such as building discipline into the deployment process.
* As teams grow, the complexity of deployments increases. Google addresses this by maintaining frequent release trains and ensuring that teams stick to established deployment schedules.

#### 10. **Conclusion**

* Continuous Delivery is about more than just pushing code into production. It is a mindset that promotes agility, collaboration, and responsiveness to user needs.
* Google’s CD processes are designed to help teams maintain velocity, reduce risk, and continuously improve the quality of their products.

By implementing CD, Google ensures that its products can adapt to market changes quickly, keeping user satisfaction high and maintaining a competitive edge .



## <mark style="color:yellow;">Chapter 25</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**Compute as a Service (CaaS)**</mark>&#x20;

#### 1. **Taming the Compute Environment**

* CaaS involves providing the computing resources necessary to run programs efficiently as an organization scales.
* Early approaches were manual and required engineers to manage machines directly. Over time, automation was introduced, leading to systems like **Google's Borg**, which inspired Kubernetes and Mesos.
* **Automation of toil** is a key principle: repetitive tasks like machine assignment, failure handling, and monitoring are automated to reduce human intervention.
* **Containerization** became a solution to multitenancy problems, allowing for efficient use of resources by isolating jobs within containers. This helped manage jobs with different resource needs without over-provisioning.

#### 2. **Writing Software for Managed Compute**

* Moving to a managed compute solution means software must be written to be resilient to changes in the environment.
* **Architecting for Failure** is critical. Engineers design software to handle failures gracefully, like being restarted on a different machine if one goes down.
* The concept of **“cattle, not pets”** is applied: rather than treating each server as unique, jobs are replicated across many servers, making it easier to scale and manage failures.
* **Batch vs. Serving**: Different jobs (batch jobs and serving jobs) have different requirements. Batch jobs, which are throughput-oriented, can tolerate interruptions, while serving jobs require high availability and low latency.

#### 3. **CaaS Over Time and Scale**

* As organizations grow, the compute environment must scale as well. The number of applications and their resource requirements will increase, necessitating automation to manage resources efficiently.
* **Rightsizing** is one approach Google has adopted, automating the process of determining the optimal amount of resources for each job.
* Borg handles both batch and serving jobs within the same system but optimizes resources to ensure each job gets the resources it needs without wasting capacity.

#### 4. **Choosing a Compute Service**

* Deciding on a compute architecture involves trade-offs between **centralization** and **customization**. While centralized systems like Borg simplify management, customization is sometimes necessary for specific workloads.
* **Level of Abstraction**: Higher levels of abstraction (e.g., serverless architectures) simplify management but require stateless applications. Serverless models like **Google Cloud Run** or **AWS Lambda** are useful for smaller teams but can be limiting as organizations grow.
* **Public vs. Private**: Public cloud services offer scalability and management overhead reduction but can lead to concerns about **vendor lock-in**. Many organizations need to balance between using public cloud infrastructure and maintaining private solutions for critical workloads.

#### 5. **Conclusion**

* Scale requires thoughtful planning of compute infrastructure. Using containers and automating resource management allows organizations to handle growing workloads efficiently.
* Serverless and container-based models are key to managing modern, scalable infrastructure, with trade-offs between flexibility and simplicity.

This chapter emphasizes that the right compute architecture is vital for scaling effectively, managing resources, and minimizing human toil through automation .
