## Automating Chime Voice Connector Reports

[Amazon Chime Voice Connector](https://aws.amazon.com/chime/voice-connector) lets you place inexpensive, secure telephone calls to over 100 countries from your on-premises phone system, using your internet connection or AWS Direct Connect. Voice Connector has no upfront fees or long-term commitments, which means you pay only for the voice minutes and phone numbers you use.  

In this sample project, we will showcase how to automate the cost reporting of Amazon Chime Voice Connector. 

Whenever you place a phone call, Voice Connector generates a Call Detail Record (CDR).  The CDR record contains information such as call time, source, and destination countries.  This sample project enriches the CDR record with the total cost of phone calls and allows you to visualize the daily, weekly and monthly costs through AWS QuickSight.

Note: This project only processes Call Detail Records (CDR) for Chime Voice Connector.  Business Connector CDRs are not considered or processed. 

## Architecture


Be sure to:

* Change the title in this README
* Edit your repository description on GitHub

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This library is licensed under the MIT-0 License. See the LICENSE file.

