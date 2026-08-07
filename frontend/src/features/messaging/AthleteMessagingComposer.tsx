import { useMemo, useState, type FormEvent } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useAthleteMessagingContacts, useAthleteMessagingTeams, useCreateAthleteConversation } from "./api";

export function AthleteMessagingComposer({ onCreated }: { onCreated: (threadId: string) => void }) {
	const teams=useAthleteMessagingTeams();
	const [teamKey,setTeamKey]=useState("");
	const team=useMemo(()=>teams.data?.find(t=>`${t.organizationId}:${t.teamId}`===teamKey) ?? teams.data?.[0],[teams.data,teamKey]);
	const contacts=useAthleteMessagingContacts(team);
	const create=useCreateAthleteConversation();
	const [title,setTitle]=useState(""); const [body,setBody]=useState(""); const [targets,setTargets]=useState<Set<string>>(new Set()); const [notice,setNotice]=useState<string|null>(null);
	if (teams.data?.length===0) return null;
	async function submit(e:FormEvent){e.preventDefault(); if(!team||!title.trim()||!body.trim()||targets.size===0)return; setNotice(null); try{const thread=await create.mutateAsync({organizationId:team.organizationId,teamId:team.teamId,title:title.trim(),targetUserIds:Array.from(targets),initialMessage:body.trim()}); setTitle("");setBody("");setTargets(new Set());onCreated(thread.id);setNotice("Athlete conversation started with guardian visibility attached.");}catch(error){setNotice(error instanceof Error?error.message:"The athlete conversation could not be started.");}}
	return <section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5" aria-labelledby="athlete-message-compose-heading">
		<h2 id="athlete-message-compose-heading" className="font-heading text-xl font-semibold text-navy">Message teammates</h2>
		<p className="mt-1 text-sm text-slate-gray">Available only for organizations with an approved messaging safety review. Linked guardians automatically receive read-only visibility.</p>
		{teams.isLoading&&<LoadingState label="Checking athlete messaging…"/>}{teams.isError&&<ErrorState message="Could not check athlete messaging." onRetry={()=>teams.refetch()}/>}
		{team && !team.athleteMessagingEnabled && <div className="mt-4 rounded-lg border border-slate-gray/20 bg-ice-white p-3 text-sm text-slate-gray">Athlete messaging is not enabled for <strong>{team.teamName}</strong>. The organization’s SafeSport/compliance gate must be approved first.</div>}
		{team && <div className="mt-4"><label htmlFor="athlete-message-team" className="text-sm font-medium text-navy">Team</label><select id="athlete-message-team" value={`${team.organizationId}:${team.teamId}`} onChange={e=>{setTeamKey(e.target.value);setTargets(new Set());}} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2">{teams.data?.map(t=><option key={t.teamId} value={`${t.organizationId}:${t.teamId}`}>{t.teamName}{t.athleteMessagingEnabled?"":" · not enabled"}</option>)}</select></div>}
		{team?.athleteMessagingEnabled && <form onSubmit={e=>void submit(e)} className="mt-4 grid gap-3">
			<div><label htmlFor="athlete-thread-title" className="text-sm font-medium text-navy">Conversation title</label><input id="athlete-thread-title" maxLength={180} required value={title} onChange={e=>setTitle(e.target.value)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2"/></div>
			<div><p className="text-sm font-medium text-navy">Teammates</p>{contacts.isLoading&&<LoadingState label="Loading eligible teammates…"/>}{contacts.isError&&<ErrorState message="Could not load eligible teammates." onRetry={()=>contacts.refetch()}/>} {contacts.data?.length===0&&<EmptyState title="No eligible teammates" description="Only activated athletes on the same current team can be selected."/>}{contacts.data&&contacts.data.length>0&&<div className="mt-2 max-h-56 overflow-y-auto rounded-lg border border-slate-gray/20 p-2">{contacts.data.map(c=><label key={c.userId} className="flex items-center gap-3 rounded-md px-2 py-2"><input type="checkbox" checked={targets.has(c.userId)} onChange={()=>setTargets(cur=>{const n=new Set(cur);n.has(c.userId)?n.delete(c.userId):n.add(c.userId);return n;})}/><span className="text-sm text-navy">{c.displayName}</span></label>)}</div>}</div>
			<div><label htmlFor="athlete-first-message" className="text-sm font-medium text-navy">Message</label><textarea id="athlete-first-message" rows={4} maxLength={5000} required value={body} onChange={e=>setBody(e.target.value)} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2"/></div>
			<Button type="submit" disabled={create.isPending||targets.size===0||!title.trim()||!body.trim()}>{create.isPending?"Starting…":"Start conversation"}</Button>
		</form>}
		{notice&&<p role="status" className="mt-3 text-sm text-slate-gray">{notice}</p>}
	</section>;
}
